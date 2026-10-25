package com.saasresearcher.learning.assessments.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GradingSnapshot(UUID attemptId, UUID examId, UUID courseId, UUID courseVersionId, UUID enrollmentId, UUID learnerId,
        String examTitle, int attemptNumber, AttemptStatus status, Instant finalizedAt, BigDecimal maxScore, BigDecimal passPercent,
        boolean autoPublish, List<Question> questions) {

    public record Question(UUID questionId, int position, QuestionType type, String prompt, BigDecimal points, String explanation,
            String gradingNotes, List<Option> options, BigDecimal autoPoints, List<UUID> selectedOptionIds, String writtenText) {
    }

    public record Option(UUID id, String label, boolean correct) {
    }
}
