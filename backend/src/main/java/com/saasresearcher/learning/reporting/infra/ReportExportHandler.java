package com.saasresearcher.learning.reporting.infra;

import com.saasresearcher.learning.classrooms.api.TeachingAccess;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.institution.api.InstitutionSettings;
import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.api.UploadSource;
import com.saasresearcher.learning.notifications.api.JobHandler;
import com.saasresearcher.learning.notifications.api.JobRequest;
import com.saasresearcher.learning.notifications.api.NotificationRequest;
import com.saasresearcher.learning.notifications.api.Notifications;
import com.saasresearcher.learning.reporting.api.ReportType;
import com.saasresearcher.learning.reporting.api.StudentProgressRow;
import com.saasresearcher.learning.reporting.api.StudentScoreRow.Score;
import com.saasresearcher.learning.reporting.api.StudentScoreRow;
import com.saasresearcher.learning.reporting.entity.ExportRow;
import com.saasresearcher.learning.reporting.repository.ExportRepository;
import com.saasresearcher.learning.reporting.service.ClassReports;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates a class CSV into private storage for the requesting staff member only. Access is checked again here and
 * at download. Columns never include credentials, answer keys or unpublished grades; cells are formula-safe, and
 * times are given in UTC and in the institution timezone.
 */
@Component
public class ReportExportHandler implements JobHandler {

    private static final int BATCH = PageQuery.MAX_SIZE;

    private final ExportRepository exports;
    private final ClassReports reports;
    private final TeachingAccess teaching;
    private final IdentityAccess identity;
    private final InstitutionSettings institution;
    private final AssetStore store;
    private final Notifications notifications;
    private final TransactionTemplate transactions;
    private final Clock clock;

    ReportExportHandler(ExportRepository exports, ClassReports reports, TeachingAccess teaching, IdentityAccess identity,
            InstitutionSettings institution, AssetStore store, Notifications notifications, TransactionTemplate transactions, Clock clock) {
        this.exports = exports;
        this.reports = reports;
        this.teaching = teaching;
        this.identity = identity;
        this.institution = institution;
        this.store = store;
        this.notifications = notifications;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public String type() {
        return ClassReports.EXPORT_JOB;
    }

    @Override
    public void handle(JobRequest job) throws IOException {
        ExportRow export = exports.find(job.aggregateId()).orElseThrow();
        if (!export.status().equals("PENDING")) {
            return;
        }
        if (!stillAllowed(export)) {
            exports.complete(export.id(), "FAILED", clock.instant());
            return;
        }
        ZoneId zone = institution.get().timezone();
        String csv = transactions.execute(status -> export.type() == ReportType.PROGRESS ? progressCsv(export, zone) : scoresCsv(export));
        store.put(new UploadSource(export.storageKey(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));
        transactions.executeWithoutResult(status -> {
            exports.complete(export.id(), "READY", clock.instant());
            notifications.notify(new NotificationRequest(export.requestedBy(), "report.ready", "Your class report is ready",
                    export.type().name().toLowerCase() + " report", "/teacher/classes/" + export.classId() + "/reports", "report-ready:" + export.id()));
        });
    }

    boolean stillAllowed(ExportRow export) {
        try {
            Actor requester = identity.requireActive(export.requestedBy());
            teaching.requireClassTeacher(export.classId(), requester);
            return true;
        } catch (ApiException revoked) {
            return false;
        }
    }

    private String progressCsv(ExportRow export, ZoneId zone) {
        List<String> header = List.of("student_name", "username", "enrollment_status", "required_lessons", "completed_required_lessons", "lesson_percent",
                "required_exams", "passed_required_exams", "pending_grading", "last_activity_utc", "last_activity_" + zone.getId(), "inactive", "completion");
        return write(header, printer -> {
            for (int page = 0; ; page++) {
                List<StudentProgressRow> rows = reports.progressRows(export.classId(), new PageQuery(page, BATCH));
                for (StudentProgressRow row : rows) {
                    printer.printRecord(cells(row.displayName(), row.username(), row.enrollmentStatus(), row.requiredLessons(),
                            row.completedRequiredLessons(), row.lessonPercent(), row.requiredExams(), row.passedRequiredExams(), row.pendingGrading(),
                            row.lastActivityAt(), local(row.lastActivityAt(), zone), row.inactive(), row.completionStatus()));
                }
                if (rows.size() < BATCH) {
                    return;
                }
            }
        });
    }

    private String scoresCsv(ExportRow export) {
        List<String> header = new ArrayList<>(List.of("student_name", "username"));
        List<StudentScoreRow> first = reports.scoreRows(export.classId(), new PageQuery(0, BATCH));
        if (!first.isEmpty()) {
            for (Score score : first.getFirst().scores()) {
                String name = score.examTitle() + (score.required() ? " (required)" : " (practice)");
                header.addAll(List.of(name + " best published", name + " maximum", name + " passed", name + " published revision", name + " pending"));
            }
        }
        return write(header, printer -> {
            for (int page = 0; ; page++) {
                List<StudentScoreRow> rows = page == 0 ? first : reports.scoreRows(export.classId(), new PageQuery(page, BATCH));
                for (StudentScoreRow row : rows) {
                    List<Object> values = new ArrayList<>(List.of(row.displayName(), row.username()));
                    for (Score score : row.scores()) {
                        values.addAll(Arrays.asList(score.bestEarned(), score.maximum(), score.passed(), score.publishedRevisionId(),
                                score.pendingGrading()));
                    }
                    printer.printRecord(cells(values.toArray()));
                }
                if (rows.size() < BATCH) {
                    return;
                }
            }
        });
    }

    private interface Body {
        void write(CSVPrinter printer) throws IOException;
    }

    private static String write(List<String> header, Body body) {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)) {
            printer.printRecord(cells(header.toArray()));
            body.write(printer);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toString();
    }

    private static Object[] cells(Object... values) {
        Object[] safe = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            safe[i] = CsvCells.safe(values[i]);
        }
        return safe;
    }

    private static String local(Instant instant, ZoneId zone) {
        return instant == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(instant.atZone(zone).toLocalDateTime().withNano(0));
    }
}
