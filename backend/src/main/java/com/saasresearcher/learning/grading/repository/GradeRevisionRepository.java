package com.saasresearcher.learning.grading.repository;

import com.saasresearcher.learning.grading.api.RevisionStatus;
import com.saasresearcher.learning.grading.entity.GradeRevision;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GradeRevisionRepository extends JpaRepository<GradeRevision, UUID> {

    List<GradeRevision> findByAttemptIdOrderByNumber(UUID attemptId);

    /** Locks all revisions of the attempt (there are few) so lifecycle changes serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GradeRevision r where r.attemptId = :attempt order by r.number")
    List<GradeRevision> lockByAttempt(@Param("attempt") UUID attemptId);

    Optional<GradeRevision> findByAttemptIdAndStatus(UUID attemptId, RevisionStatus status);

    /** One row of a course's grading queue: finalized attempts with their latest revision, open work first. */
    interface QueueRow {

        UUID getAttemptId();

        UUID getCourseVersionId();

        String getLearnerName();

        String getUsername();

        String getExamTitle();

        int getAttemptNumber();

        Instant getFinalizedAt();

        short getGradeStatus();

        int getRevisionNumber();

        BigDecimal getPublishedEarned();

        BigDecimal getMaximum();
    }

    // RevisionStatus codes: 0 DRAFT, 1 FINALIZED, 2 PUBLISHED; open work is 0 and 1.
    String QUEUE_FROM = """
            from exam_attempts a
            join exams ex on ex.id = a.exam_id
            join users u on u.id = a.learner_id
            join grade_revisions r on r.attempt_id = a.id
                and r.revision_number = (select max(x.revision_number) from grade_revisions x where x.attempt_id = a.id)
            where a.course_id = :course and a.status <> 0
              and (:allVersions or a.course_version_id = :version)
              and (:filter = 'ALL' or (:filter = 'PENDING' and r.status in (0, 1)) or (:filter = 'PUBLISHED' and r.status = 2))
            """;

    @Query(nativeQuery = true, value = """
            select a.id as attempt_id, a.course_version_id, u.display_name as learner_name, u.username, ex.title as exam_title, a.attempt_number,
                a.finalized_at, r.status as grade_status, r.revision_number, a.published_earned, a.max_score as maximum
            """ + QUEUE_FROM + """
            order by case when r.status in (0, 1) then 0 else 1 end, a.finalized_at, a.id
            """, countQuery = "select count(*) " + QUEUE_FROM)
    Page<QueueRow> queue(@Param("course") UUID courseId, @Param("allVersions") boolean allVersions, @Param("version") UUID courseVersionId,
            @Param("filter") String filter, Pageable pageable);
}
