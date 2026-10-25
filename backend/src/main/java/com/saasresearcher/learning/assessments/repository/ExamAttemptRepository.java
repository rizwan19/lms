package com.saasresearcher.learning.assessments.repository;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.entity.ExamAttempt;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a where a.id = :id")
    Optional<ExamAttempt> lockById(@Param("id") UUID id);

    Optional<ExamAttempt> findByEnrollmentIdAndExamIdAndStatus(UUID enrollmentId, UUID examId, AttemptStatus status);

    List<ExamAttempt> findByEnrollmentIdAndExamIdOrderByAttemptNumber(UUID enrollmentId, UUID examId);

    @Query("select a.id from ExamAttempt a where a.status = com.saasresearcher.learning.assessments.api.AttemptStatus.IN_PROGRESS"
            + " and a.expiresAt <= :now order by a.expiresAt, a.id")
    List<UUID> dueExpired(@Param("now") Instant now, Limit limit);

    @Query("select a from ExamAttempt a where a.learnerId = :learner"
            + " and a.status <> com.saasresearcher.learning.assessments.api.AttemptStatus.IN_PROGRESS order by a.finalizedAt desc, a.id")
    List<ExamAttempt> finalizedOf(@Param("learner") UUID learnerId);

    @Query("select count(a) > 0 from ExamAttempt a where a.enrollmentId = :enrollment and a.examId = :exam"
            + " and a.status <> com.saasresearcher.learning.assessments.api.AttemptStatus.IN_PROGRESS and a.publishedAt is null")
    boolean anyUnpublished(@Param("enrollment") UUID enrollmentId, @Param("exam") UUID examId);

    boolean existsByEnrollmentIdAndExamIdAndPublishedPassedTrue(UUID enrollmentId, UUID examId);
}
