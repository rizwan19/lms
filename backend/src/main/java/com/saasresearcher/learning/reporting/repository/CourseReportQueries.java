package com.saasresearcher.learning.reporting.repository;

import com.saasresearcher.learning.reporting.entity.ReportExport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only projections over the transactional tables (no separate analytics store). Only published results are used
 * for scores; pending grading is reported as a count, never as zero.
 */
public interface CourseReportQueries extends Repository<ReportExport, UUID> {

    interface Learner {

        UUID getEnrollmentId();

        UUID getCourseVersionId();

        String getDisplayName();

        String getUsername();

        short getStatus();

        Instant getGrantedAt();
    }

    String LEARNERS = """
            from course_enrollments e join users u on u.id = e.learner_id
            where e.course_id = :course and (:allVersions or e.course_version_id = :version)
            """;

    @Query(nativeQuery = true, value = "select e.id as enrollment_id, e.course_version_id, u.display_name, u.username, e.status, e.granted_at "
            + LEARNERS + " order by lower(u.display_name), e.id", countQuery = "select count(*) " + LEARNERS)
    Page<Learner> learners(@Param("course") UUID courseId, @Param("allVersions") boolean allVersions, @Param("version") UUID versionId,
            Pageable pageable);

    @Query(nativeQuery = true, value = """
            select count(*) from lesson_progress where enrollment_id = :enrollment and completed_at is not null and lesson_id in (:lessons)
            """)
    int completedLessons(@Param("enrollment") UUID enrollmentId, @Param("lessons") Collection<UUID> lessonIds);

    @Query(nativeQuery = true, value = """
            select greatest(
                (select max(last_activity_at) from lesson_progress where enrollment_id = :enrollment),
                (select max(coalesce(finalized_at, started_at)) from exam_attempts where enrollment_id = :enrollment),
                (select max(x.saved_at) from attempt_answers x join exam_attempts a on a.id = x.attempt_id where a.enrollment_id = :enrollment))
            """)
    Optional<Instant> lastActivity(@Param("enrollment") UUID enrollmentId);

    interface RequiredExams {

        int getRequired();

        int getPassed();
    }

    @Query(nativeQuery = true, value = """
            select count(*) as required,
                count(*) filter (where exists (select 1 from exam_attempts a where a.exam_id = x.id and a.enrollment_id = e.id
                    and a.published_passed)) as passed
            from exams x join course_enrollments e on e.course_version_id = x.course_version_id
            where e.id = :enrollment and x.required
            """)
    RequiredExams requiredExams(@Param("enrollment") UUID enrollmentId);

    @Query(nativeQuery = true, value = """
            select count(*) from exam_attempts where enrollment_id = :enrollment and status <> 0 and published_at is null
            """)
    int pendingGrading(@Param("enrollment") UUID enrollmentId);

    @Query(nativeQuery = true, value = "select status from course_completions where enrollment_id = :enrollment")
    Optional<Short> completionStatus(@Param("enrollment") UUID enrollmentId);

    interface ExamColumn {

        UUID getId();

        String getTitle();

        boolean getRequired();
    }

    @Query(nativeQuery = true, value = """
            select id, title, required from exams where course_version_id = :version order by created_at, id
            """)
    List<ExamColumn> exams(@Param("version") UUID courseVersionId);

    interface BestScore {

        UUID getExamId();

        BigDecimal getPublishedEarned();

        BigDecimal getPublishedMaximum();

        Boolean getPublishedPassed();

        UUID getPublishedRevisionId();
    }

    /** Best published attempt per exam for one enrollment (highest score, then latest publication). */
    @Query(nativeQuery = true, value = """
            select distinct on (a.exam_id) a.exam_id, a.published_earned, a.published_maximum, a.published_passed,
                a.published_revision_id
            from exam_attempts a where a.enrollment_id = :enrollment and a.published_at is not null
            order by a.exam_id, a.published_earned desc, a.published_at desc
            """)
    List<BestScore> bestScores(@Param("enrollment") UUID enrollmentId);

    @Query(nativeQuery = true, value = """
            select distinct exam_id from exam_attempts where enrollment_id = :enrollment and status <> 0 and published_at is null
            """)
    List<UUID> pendingExams(@Param("enrollment") UUID enrollmentId);
}
