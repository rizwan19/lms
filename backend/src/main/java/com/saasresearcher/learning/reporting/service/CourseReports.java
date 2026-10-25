package com.saasresearcher.learning.reporting.service;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.completion.api.CompletionStatus;
import com.saasresearcher.learning.courses.api.CourseCatalogService;
import com.saasresearcher.learning.courses.api.CourseOwnershipService;
import com.saasresearcher.learning.courses.api.CourseVersionInfo;
import com.saasresearcher.learning.enrollment.api.EnrollmentStatus;
import com.saasresearcher.learning.notifications.api.DurableJobs;
import com.saasresearcher.learning.notifications.api.JobRequest;
import com.saasresearcher.learning.reporting.api.LearnerProgressRow;
import com.saasresearcher.learning.reporting.api.LearnerScoreRow;
import com.saasresearcher.learning.reporting.api.LearnerScoreRow.Score;
import com.saasresearcher.learning.reporting.api.ReportType;
import com.saasresearcher.learning.reporting.api.ReportingService;
import com.saasresearcher.learning.reporting.config.ReportingProperties;
import com.saasresearcher.learning.reporting.entity.ReportExport;
import com.saasresearcher.learning.reporting.repository.CourseReportQueries;
import com.saasresearcher.learning.reporting.repository.CourseReportQueries.BestScore;
import com.saasresearcher.learning.reporting.repository.CourseReportQueries.ExamColumn;
import com.saasresearcher.learning.reporting.repository.CourseReportQueries.Learner;
import com.saasresearcher.learning.reporting.repository.CourseReportQueries.RequiredExams;
import com.saasresearcher.learning.reporting.repository.ReportExportRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course reports for the course's author only, optionally filtered to one edition. Each learner's lesson requirements
 * come from their own pinned edition.
 */
@Service
public class CourseReports implements ReportingService {

    public static final String EXPORT_JOB = "reporting.export";

    private final CourseReportQueries queries;
    private final ReportExportRepository exports;
    private final CourseOwnershipService ownership;
    private final CourseCatalogService courses;
    private final DurableJobs jobs;
    private final ReportingProperties properties;
    private final Clock clock;

    CourseReports(CourseReportQueries queries, ReportExportRepository exports, CourseOwnershipService ownership, CourseCatalogService courses, DurableJobs jobs,
                  ReportingProperties properties, Clock clock) {
        this.queries = queries;
        this.exports = exports;
        this.ownership = ownership;
        this.courses = courses;
        this.jobs = jobs;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LearnerProgressRow> courseProgress(UUID courseId, UUID versionId, Actor actor, PageQuery query) {
        ownership.requireAuthor(courseId, actor);
        requireEdition(courseId, versionId);
        Map<UUID, Set<UUID>> requiredLessons = new HashMap<>();
        return PageResponse.of(learners(courseId, versionId, query), learner -> progress(learner, requiredLessons));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LearnerScoreRow> courseScores(UUID courseId, UUID versionId, Actor actor, PageQuery query) {
        ownership.requireAuthor(courseId, actor);
        UUID edition = scoreEdition(courseId, versionId);
        List<ExamColumn> exams = queries.exams(edition);
        return PageResponse.of(learners(courseId, edition, query), learner -> scores(learner, exams));
    }

    @Override
    @Transactional
    public UUID requestExport(UUID courseId, UUID versionId, ReportType type, Actor actor) {
        ownership.requireAuthor(courseId, actor);
        requireEdition(courseId, versionId);
        ReportExport export = exports.saveAndFlush(new ReportExport(courseId, versionId, actor.userId(), type, clock.instant()));
        jobs.enqueue(JobRequest.now(EXPORT_JOB, export.getId(), "report-export:" + export.getId(), clock.instant()));
        return export.getId();
    }

    @Transactional(readOnly = true)
    public Optional<ReportExport> export(UUID exportId) {
        return exports.findById(exportId);
    }

    @Transactional(readOnly = true)
    public List<ReportExport> exportsOf(UUID courseId, Actor actor) {
        ownership.requireAuthor(courseId, actor);
        return exports.findByRequestedByAndCourseIdOrderByCreatedAtDesc(actor.userId(), courseId, Limit.of(20));
    }

    /** Rechecked at generation and at download: the requester must still be the course's active author. */
    public void requireRequesterStillAuthor(ReportExport export, Actor requester) {
        ownership.requireAuthor(export.getCourseId(), requester);
    }

    public List<LearnerProgressRow> progressRows(UUID courseId, UUID versionId, PageQuery query) {
        Map<UUID, Set<UUID>> requiredLessons = new HashMap<>();
        return learners(courseId, versionId, query).map(learner -> progress(learner, requiredLessons)).getContent();
    }

    /** Score rows for export; returns the edition's exam columns with the rows. */
    public ScorePage scoreRows(UUID courseId, UUID versionId, PageQuery query) {
        UUID edition = scoreEdition(courseId, versionId);
        List<ExamColumn> exams = queries.exams(edition);
        return new ScorePage(exams, learners(courseId, edition, query).map(learner -> scores(learner, exams)).getContent());
    }

    public record ScorePage(List<ExamColumn> exams, List<LearnerScoreRow> rows) {
    }

    private Page<Learner> learners(UUID courseId, UUID versionId, PageQuery query) {
        return queries.learners(courseId, versionId == null, versionId == null ? courseId : versionId, query.pageable(Sort.unsorted()));
    }

    /** {@code requiredLessons} caches the required lessons per edition within one page. */
    private LearnerProgressRow progress(Learner learner, Map<UUID, Set<UUID>> requiredLessons) {
        Set<UUID> required = requiredLessons.computeIfAbsent(learner.getCourseVersionId(),
                version -> courses.requirePublished(version).requiredLessonIds());
        int completed = required.isEmpty() ? 0 : queries.completedLessons(learner.getEnrollmentId(), required);
        Instant lastActivity = queries.lastActivity(learner.getEnrollmentId()).orElse(null);
        RequiredExams exams = queries.requiredExams(learner.getEnrollmentId());
        Instant reference = lastActivity == null ? learner.getGrantedAt() : lastActivity;
        boolean inactive = reference.isBefore(clock.instant().minus(Duration.ofDays(properties.inactivityDays())));
        Integer percent = required.isEmpty() ? null : (int) Math.floor(completed * 100.0 / required.size());
        return new LearnerProgressRow(learner.getEnrollmentId(), learner.getCourseVersionId(), learner.getDisplayName(), learner.getUsername(),
                EnrollmentStatus.fromCode(learner.getStatus()).name(), required.size(), completed, percent, exams.getRequired(), exams.getPassed(),
                queries.pendingGrading(learner.getEnrollmentId()), lastActivity, inactive,
                queries.completionStatus(learner.getEnrollmentId()).map(code -> CompletionStatus.fromCode(code).name()).orElse("NOT_COMPLETED"));
    }

    private LearnerScoreRow scores(Learner learner, List<ExamColumn> exams) {
        Map<UUID, BestScore> best = queries.bestScores(learner.getEnrollmentId()).stream()
                .collect(Collectors.toMap(BestScore::getExamId, Function.identity()));
        List<UUID> pending = queries.pendingExams(learner.getEnrollmentId());
        List<Score> scores = exams.stream().map(exam -> {
            BestScore found = best.get(exam.getId());
            return new Score(exam.getId(), exam.getTitle(), exam.getRequired(), found == null ? null : found.getPublishedEarned(),
                    found == null ? null : found.getPublishedMaximum(), found == null ? null : found.getPublishedPassed(),
                    found == null ? null : found.getPublishedRevisionId(), pending.contains(exam.getId()));
        }).toList();
        return new LearnerScoreRow(learner.getEnrollmentId(), learner.getDisplayName(), learner.getUsername(), scores);
    }

    private void requireEdition(UUID courseId, UUID versionId) {
        if (versionId != null) {
            CourseVersionInfo version = courses.requireVersion(versionId);
            if (!version.courseId().equals(courseId) || version.draft()) {
                throw ApiException.notFound();
            }
        }
    }

    /** Score columns are the exams of one edition: the requested one, else the latest published edition. */
    private UUID scoreEdition(UUID courseId, UUID versionId) {
        requireEdition(courseId, versionId);
        return versionId != null ? versionId
                : courses.latestPublished(courseId).map(course -> course.versionId()).orElseThrow(ApiException::notFound);
    }
}
