package com.saasresearcher.learning.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Explicit completion and monotonic resume position of one lesson in one enrollment, on the enrollment's edition. */
@Getter
@Entity
@Table(name = "lesson_progress")
public class LessonProgress {

    @Id
    private UUID id;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "lesson_id", nullable = false, updatable = false)
    private UUID lessonId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "course_version_id", nullable = false, updatable = false)
    private UUID courseVersionId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "resume_seconds", nullable = false)
    private long resumeSeconds;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(nullable = false)
    private long revision;

    protected LessonProgress() {
    }

    public LessonProgress(UUID enrollmentId, UUID courseId, UUID courseVersionId, UUID lessonId) {
        this.id = UUID.randomUUID();
        this.enrollmentId = enrollmentId;
        this.courseId = courseId;
        this.courseVersionId = courseVersionId;
        this.lessonId = lessonId;
    }

    /** Applies one acknowledged study action; the resume position never moves backwards. */
    public void record(boolean completed, long resumeSeconds, Instant now) {
        this.completedAt = completed ? (completedAt == null ? now : completedAt) : null;
        this.resumeSeconds = Math.max(this.resumeSeconds, resumeSeconds);
        this.lastActivityAt = now;
        this.revision++;
    }

    public boolean completed() {
        return completedAt != null;
    }
}
