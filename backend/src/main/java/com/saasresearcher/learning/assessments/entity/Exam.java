package com.saasresearcher.learning.assessments.entity;

import com.saasresearcher.learning.assessments.api.ExamMode;
import com.saasresearcher.learning.assessments.api.ExamSchedule;
import com.saasresearcher.learning.assessments.api.FeedbackPolicy;
import com.saasresearcher.learning.assessments.api.ResultPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "exams")
public class Exam {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "course_version_id", nullable = false, updatable = false)
    private UUID courseVersionId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String instructions;

    @Column(name = "pass_percent", nullable = false)
    private BigDecimal passPercent;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private ExamMode mode;

    @Column(name = "opens_at")
    private Instant opensAt;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "result_policy", nullable = false)
    private ResultPolicy resultPolicy;

    @Column(name = "feedback_policy", nullable = false)
    private FeedbackPolicy feedbackPolicy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Exam() {
    }

    public Exam(UUID courseId, UUID courseVersionId, String title, String instructions, BigDecimal passPercent, boolean required,
            ExamSchedule schedule, Instant now) {
        this.id = UUID.randomUUID();
        this.courseId = courseId;
        this.courseVersionId = courseVersionId;
        this.title = title;
        this.instructions = instructions;
        this.passPercent = passPercent;
        this.required = required;
        this.createdAt = now;
        reschedule(schedule, now);
    }

    public Exam copyTo(UUID draftVersionId, Instant now) {
        Exam copy = new Exam(courseId, draftVersionId, title, instructions, passPercent, required, schedule(), now);
        copy.createdAt = createdAt;
        return copy;
    }

    public void edit(String title, String instructions, BigDecimal passPercent, Instant now) {
        this.title = title;
        this.instructions = instructions;
        this.passPercent = passPercent;
        this.updatedAt = now;
    }

    public void reschedule(ExamSchedule schedule, Instant now) {
        this.mode = schedule.mode();
        this.opensAt = schedule.opensAt();
        this.closesAt = schedule.closesAt();
        this.durationMinutes = schedule.durationMinutes();
        this.maxAttempts = schedule.maxAttempts();
        this.resultPolicy = schedule.resultPolicy();
        this.feedbackPolicy = schedule.feedbackPolicy();
        this.updatedAt = now;
    }

    public void setRequired(boolean required, Instant now) {
        this.required = required;
        this.updatedAt = now;
    }

    public boolean isOpen(Instant now) {
        return mode == ExamMode.SELF_PACED || (!now.isBefore(opensAt) && now.isBefore(closesAt));
    }

    public boolean hasClosed(Instant now) {
        return mode == ExamMode.SCHEDULED && !now.isBefore(closesAt);
    }

    public Instant deadline(Instant startedAt) {
        Instant timed = startedAt.plus(Duration.ofMinutes(durationMinutes));
        return mode == ExamMode.SCHEDULED && closesAt.isBefore(timed) ? closesAt : timed;
    }

    public ExamSchedule schedule() {
        return new ExamSchedule(mode, opensAt, closesAt, durationMinutes, maxAttempts, resultPolicy, feedbackPolicy);
    }
}
