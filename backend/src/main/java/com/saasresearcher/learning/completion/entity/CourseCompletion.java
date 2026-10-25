package com.saasresearcher.learning.completion.entity;

import com.saasresearcher.learning.completion.api.CompletionSnapshot;
import com.saasresearcher.learning.completion.api.CompletionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** One completion per enrollment: ACHIEVED, or INVALIDATED when a published correction removes a required pass. */
@Getter
@Entity
@Table(name = "course_completions")
public class CourseCompletion {

    @Id
    private UUID id;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "course_version_id", nullable = false, updatable = false)
    private UUID courseVersionId;

    @Column(nullable = false)
    private CompletionStatus status;

    @Column(name = "achieved_at", nullable = false, updatable = false)
    private Instant achievedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Version
    private long version;

    protected CourseCompletion() {
    }

    public CourseCompletion(UUID enrollmentId, UUID learnerId, UUID courseId, UUID courseVersionId, Instant now) {
        this.id = UUID.randomUUID();
        this.enrollmentId = enrollmentId;
        this.learnerId = learnerId;
        this.courseId = courseId;
        this.courseVersionId = courseVersionId;
        this.status = CompletionStatus.ACHIEVED;
        this.achievedAt = now;
        this.statusChangedAt = now;
    }

    public void setStatus(CompletionStatus status, Instant now) {
        this.status = status;
        this.statusChangedAt = now;
    }

    public CompletionSnapshot toSnapshot() {
        return new CompletionSnapshot(id, enrollmentId, learnerId, courseId, courseVersionId, status, achievedAt);
    }
}
