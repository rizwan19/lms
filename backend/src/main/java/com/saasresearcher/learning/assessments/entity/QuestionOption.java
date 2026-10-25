package com.saasresearcher.learning.assessments.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "question_options")
public class QuestionOption {

    @Id
    private UUID id;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean correct;

    protected QuestionOption() {
    }

    public QuestionOption(UUID id, UUID questionId, int position, String label, boolean correct) {
        this.id = id;
        this.questionId = questionId;
        this.position = position;
        this.label = label;
        this.correct = correct;
    }
}
