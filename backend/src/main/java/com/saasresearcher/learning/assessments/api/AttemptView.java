package com.saasresearcher.learning.assessments.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttemptView(UUID id, UUID examId, UUID enrollmentId, String examTitle, String instructions, int attemptNumber,
                          int maxAttempts, AttemptStatus status, Instant startedAt, Instant expiresAt, Instant serverNow, Instant finalizedAt,
                          List<LearnerQuestionView> questions, List<SavedAnswer> answers) {

    public record LearnerQuestionView(UUID id, int position, QuestionType type, String prompt, BigDecimal points, List<OptionView> options) {
    }

    public record OptionView(UUID id, String label) {
    }

    public record SavedAnswer(UUID questionId, List<UUID> selectedOptionIds, String writtenText, Instant savedAt) {
    }
}
