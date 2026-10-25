package com.saasresearcher.learning.reporting.infra;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.api.UploadSource;
import com.saasresearcher.learning.notifications.api.JobHandler;
import com.saasresearcher.learning.notifications.api.JobRequest;
import com.saasresearcher.learning.notifications.api.NotificationRequest;
import com.saasresearcher.learning.notifications.api.Notifications;
import com.saasresearcher.learning.reporting.api.ExportStatus;
import com.saasresearcher.learning.reporting.api.LearnerProgressRow;
import com.saasresearcher.learning.reporting.api.LearnerScoreRow.Score;
import com.saasresearcher.learning.reporting.api.LearnerScoreRow;
import com.saasresearcher.learning.reporting.api.ReportType;
import com.saasresearcher.learning.reporting.entity.ReportExport;
import com.saasresearcher.learning.reporting.repository.CourseReportQueries.ExamColumn;
import com.saasresearcher.learning.reporting.repository.ReportExportRepository;
import com.saasresearcher.learning.reporting.service.CourseReports.ScorePage;
import com.saasresearcher.learning.reporting.service.CourseReports;
import com.saasresearcher.learning.workspace.api.WorkspaceService;
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
 * Generates a course CSV into private storage for the requesting author only. Authorship and account status are checked
 * again here and at download. Columns never include credentials, answer keys or unpublished grades; cells are formula-safe, and
 * times are given in UTC and in the workspace timezone.
 */
@Component
public class ReportExportHandler implements JobHandler {

    private static final int BATCH = PageQuery.MAX_SIZE;

    private final ReportExportRepository exports;
    private final CourseReports reports;
    private final IdentityAccess identity;
    private final WorkspaceService workspace;
    private final AssetStore store;
    private final Notifications notifications;
    private final TransactionTemplate transactions;
    private final Clock clock;

    ReportExportHandler(ReportExportRepository exports, CourseReports reports, IdentityAccess identity, WorkspaceService workspace, AssetStore store,
            Notifications notifications, TransactionTemplate transactions, Clock clock) {
        this.exports = exports;
        this.reports = reports;
        this.identity = identity;
        this.workspace = workspace;
        this.store = store;
        this.notifications = notifications;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public String type() {
        return CourseReports.EXPORT_JOB;
    }

    @Override
    public void handle(JobRequest job) throws IOException {
        ReportExport export = exports.findById(job.aggregateId()).orElseThrow();
        if (export.getStatus() != ExportStatus.PENDING) {
            return;
        }
        if (!stillAllowed(export)) {
            transactions.executeWithoutResult(status -> complete(export, ExportStatus.FAILED));
            return;
        }
        ZoneId zone = workspace.getWorkspaceData().timezone();
        String csv = transactions.execute(status -> export.getType() == ReportType.PROGRESS ? progressCsv(export, zone) : scoresCsv(export));
        store.put(new UploadSource(export.getStorageKey(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));
        transactions.executeWithoutResult(status -> {
            complete(export, ExportStatus.READY);
            notifications.notify(new NotificationRequest(export.getRequestedBy(), "report.ready", "Your course report is ready",
                    export.getType().name().toLowerCase() + " report", "/courses/" + export.getCourseId() + "/reports", "report-ready:" + export.getId()));
        });
    }

    private void complete(ReportExport export, ExportStatus status) {
        exports.findById(export.getId()).orElseThrow().complete(status, clock.instant());
    }

    boolean stillAllowed(ReportExport export) {
        try {
            Actor requester = identity.requireActive(export.getRequestedBy());
            reports.requireRequesterStillAuthor(export, requester);
            return true;
        } catch (ApiException revoked) {
            return false;
        }
    }

    private String progressCsv(ReportExport export, ZoneId zone) {
        List<String> header = List.of("learner_name", "username", "enrollment_status", "required_lessons", "completed_required_lessons", "lesson_percent",
                "required_exams", "passed_required_exams", "pending_grading", "last_activity_utc", "last_activity_" + zone.getId(), "inactive", "completion");
        return write(header, printer -> {
            for (int page = 0; ; page++) {
                List<LearnerProgressRow> rows = reports.progressRows(export.getCourseId(), export.getCourseVersionId(), new PageQuery(page, BATCH));
                for (LearnerProgressRow row : rows) {
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

    private String scoresCsv(ReportExport export) {
        List<String> header = new ArrayList<>(List.of("learner_name", "username"));
        ScorePage first = reports.scoreRows(export.getCourseId(), export.getCourseVersionId(), new PageQuery(0, BATCH));
        for (ExamColumn exam : first.exams()) {
            String name = exam.getTitle() + (exam.getRequired() ? " (required)" : " (practice)");
            header.addAll(List.of(name + " best published", name + " maximum", name + " passed", name + " published revision", name + " pending"));
        }
        return write(header, printer -> {
            for (int page = 0; ; page++) {
                List<LearnerScoreRow> rows = page == 0 ? first.rows()
                        : reports.scoreRows(export.getCourseId(), export.getCourseVersionId(), new PageQuery(page, BATCH)).rows();
                for (LearnerScoreRow row : rows) {
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
