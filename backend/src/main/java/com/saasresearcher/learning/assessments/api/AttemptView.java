package com.saasresearcher.learning.assessments.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a student sees of their attempt: server time and deadline, the questions in their own stable order and their
 * saved answers. Never contains keys, explanations, grading notes or unpublished scores.
 */
public record AttemptView(UUID id, UUID assignmentId, UUID enrollmentId, String examTitle, String instructions, int attemptNumber,
        int maxAttempts, AttemptStatus status, Instant startedAt, Instant expiresAt, Instant serverNow, Instant finalizedAt,
        List<QuestionView> questions, List<SavedAnswer> answers) {

    public record QuestionView(UUID id, int position, QuestionType type, String prompt, BigDecimal points, List<OptionView> options) {
    }

    public record OptionView(UUID id, String label) {
    }

    public record SavedAnswer(UUID questionId, List<UUID> selectedOptionIds, String writtenText, long revision, Instant savedAt) {
    }
}
