package com.saasresearcher.learning.assessments.entity;

import com.saasresearcher.learning.assessments.api.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "exam_questions")
public class ExamQuestion {

    @Id
    private UUID id;

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private QuestionType type;

    @Column(nullable = false)
    private String prompt;

    @Column(nullable = false)
    private BigDecimal points;

    @Column(nullable = false)
    private String explanation;

    @Column(name = "grading_notes", nullable = false)
    private String gradingNotes;

    protected ExamQuestion() {
    }

    public ExamQuestion(UUID id, UUID examId, int position, QuestionType type, String prompt, BigDecimal points, String explanation,
            String gradingNotes) {
        this.id = id;
        this.examId = examId;
        this.position = position;
        this.type = type;
        this.prompt = prompt;
        this.points = points;
        this.explanation = explanation;
        this.gradingNotes = gradingNotes;
    }
}
