package com.saasresearcher.learning.assessments.entity;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "exam_attempts")
public class ExamAttempt {

    @Id
    private UUID id;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "course_version_id", nullable = false, updatable = false)
    private UUID courseVersionId;

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(nullable = false)
    private AttemptStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "objective_score")
    private BigDecimal objectiveScore;

    @Column(name = "max_score", nullable = false, updatable = false)
    private BigDecimal maxScore;

    @Column(name = "published_revision_id")
    private UUID publishedRevisionId;

    @Column(name = "published_earned")
    private BigDecimal publishedEarned;

    @Column(name = "published_maximum")
    private BigDecimal publishedMaximum;

    @Column(name = "published_passed")
    private Boolean publishedPassed;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    private long version;

    protected ExamAttempt() {
    }

    public ExamAttempt(Exam exam, UUID enrollmentId, UUID learnerId, int attemptNumber, Instant startedAt, BigDecimal maxScore) {
        this.id = UUID.randomUUID();
        this.enrollmentId = enrollmentId;
        this.courseId = exam.getCourseId();
        this.courseVersionId = exam.getCourseVersionId();
        this.examId = exam.getId();
        this.learnerId = learnerId;
        this.attemptNumber = attemptNumber;
        this.status = AttemptStatus.IN_PROGRESS;
        this.startedAt = startedAt;
        this.expiresAt = exam.deadline(startedAt);
        this.maxScore = maxScore;
    }

    public boolean isExpired() {
        return status == AttemptStatus.IN_PROGRESS && !Instant.now().isBefore(expiresAt);
    }

    public void finish(AttemptStatus status, BigDecimal objectiveScore, Instant now) {
        this.status = status;
        this.objectiveScore = objectiveScore;
        this.finalizedAt = now;
    }

    public void recordPublished(UUID revisionId, BigDecimal earned, BigDecimal maximum, BigDecimal passPercent, Instant publishedAt) {
        this.publishedRevisionId = revisionId;
        this.publishedEarned = earned;
        this.publishedMaximum = maximum;
        this.publishedPassed = earned.multiply(BigDecimal.valueOf(100)).compareTo(passPercent.multiply(maximum)) >= 0;
        this.publishedAt = publishedAt;
        if (status != AttemptStatus.EXPIRED) {
            this.status = AttemptStatus.GRADED;
        }
    }
}
