package com.saasresearcher.learning.grading.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** A private per-question grade of a revision; {@code points} null means not graded yet (written answers). */
@Getter
@Entity
@Table(name = "answer_grades")
public class AnswerGrade {

    @Id
    private UUID id;

    @Column(name = "grade_revision_id", nullable = false, updatable = false)
    private UUID revisionId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    private BigDecimal points;

    @Column(nullable = false)
    private String feedback;

    @Column(name = "graded_by")
    private UUID gradedBy;

    @Column(name = "graded_at")
    private Instant gradedAt;

    protected AnswerGrade() {
    }

    public AnswerGrade(UUID revisionId, UUID questionId, BigDecimal points, String feedback, UUID gradedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.revisionId = revisionId;
        this.questionId = questionId;
        this.feedback = feedback;
        this.gradedBy = gradedBy;
        grade(points, feedback, gradedBy, now);
    }

    public void grade(BigDecimal points, String feedback, UUID gradedBy, Instant now) {
        this.points = points;
        this.feedback = feedback;
        this.gradedBy = gradedBy;
        this.gradedAt = points == null ? null : now;
    }
}
