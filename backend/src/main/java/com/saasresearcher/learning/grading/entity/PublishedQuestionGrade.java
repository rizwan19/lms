package com.saasresearcher.learning.grading.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/** Immutable copy of a published revision's grades; the only grades learners ever read. */
@Getter
@Entity
@Table(name = "published_question_grades")
public class PublishedQuestionGrade {

    @Id
    private UUID id;

    @Column(name = "grade_revision_id", nullable = false, updatable = false)
    private UUID revisionId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(nullable = false, updatable = false)
    private BigDecimal points;

    @Column(nullable = false, updatable = false)
    private String feedback;

    protected PublishedQuestionGrade() {
    }

    public PublishedQuestionGrade(UUID revisionId, UUID questionId, BigDecimal points, String feedback) {
        this.id = UUID.randomUUID();
        this.revisionId = revisionId;
        this.questionId = questionId;
        this.points = points;
        this.feedback = feedback;
    }
}
