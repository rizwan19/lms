package com.saasresearcher.learning.assessments.entity;

import com.saasresearcher.learning.assessments.api.QuestionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Author-side question including its private key (correct options, grading notes). */
public record ExamQuestion(UUID id, QuestionType type, String prompt, BigDecimal points, String explanation, String gradingNotes,
        List<Option> options) {

    public ExamQuestion {
        options = List.copyOf(options);
    }

    public record Option(UUID id, String label, boolean correct) {
    }
}
