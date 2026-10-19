package com.saasresearcher.learning.reporting.internal;

import com.saasresearcher.learning.classrooms.api.ClassDirectory;
import com.saasresearcher.learning.classrooms.api.TeachingAccess;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.courses.api.CourseCatalog;
import com.saasresearcher.learning.notifications.api.DurableJobs;
import com.saasresearcher.learning.notifications.api.JobRequest;
import com.saasresearcher.learning.reporting.api.ReportType;
import com.saasresearcher.learning.reporting.api.ReportingService;
import com.saasresearcher.learning.reporting.api.StudentProgressRow;
import com.saasresearcher.learning.reporting.api.StudentScoreRow;
import com.saasresearcher.learning.reporting.internal.ClassReportQueries.Assignment;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Class reports for assigned class teachers and administrators. */
@Service
class ClassReports implements ReportingService {

    static final String EXPORT_JOB = "reporting.export";

    private final ClassReportQueries queries;
    private final ExportRepository exports;
    private final TeachingAccess teaching;
    private final ClassDirectory classes;
    private final CourseCatalog courses;
    private final DurableJobs jobs;
    private final ReportingProperties properties;
    private final Clock clock;

    ClassReports(ClassReportQueries queries, ExportRepository exports, TeachingAccess teaching, ClassDirectory classes, CourseCatalog courses,
            DurableJobs jobs, ReportingProperties properties, Clock clock) {
        this.queries = queries;
        this.exports = exports;
        this.teaching = teaching;
        this.classes = classes;
        this.courses = courses;
        this.jobs = jobs;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentProgressRow> classProgress(UUID classId, Actor actor, PageQuery query) {
        teaching.requireClassTeacher(classId, actor);
        return PageResponse.of(progressRows(classId, query), query, queries.countStudents(classId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentScoreRow> classScores(UUID classId, Actor actor, PageQuery query) {
        teaching.requireClassTeacher(classId, actor);
        return PageResponse.of(scoreRows(classId, query), query, queries.countStudents(classId));
    }

    @Override
    @Transactional
    public UUID requestExport(UUID classId, ReportType type, Actor actor) {
        teaching.requireClassTeacher(classId, actor);
        UUID id = UUID.randomUUID();
        exports.insert(id, classId, actor.userId(), type, clock.instant());
        jobs.enqueue(JobRequest.now(EXPORT_JOB, id, "report-export:" + id, clock.instant()));
        return id;
    }

    List<StudentProgressRow> progressRows(UUID classId, PageQuery query) {
        Set<UUID> required = courses.requirePublished(classes.require(classId).courseVersionId()).requiredLessonIds();
        Instant now = clock.instant();
        return queries.students(classId, query).stream()
                .map(student -> queries.progress(student, required, properties.inactivityDays(), now))
                .toList();
    }

    List<StudentScoreRow> scoreRows(UUID classId, PageQuery query) {
        List<Assignment> assignments = queries.assignments(classId);
        return queries.students(classId, query).stream()
                .map(student -> new StudentScoreRow(student.enrollmentId(), student.displayName(), student.username(),
                        queries.scores(student.enrollmentId(), assignments)))
                .toList();
    }
}
