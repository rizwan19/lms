package com.saasresearcher.learning.assessments.dto;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Student-facing exam listings. No keys, no unpublished scores. */
public final class StudentExamViews {

    private StudentExamViews() {
    }

    public record AttemptSummary(UUID id, int attemptNumber, AttemptStatus status, Instant finalizedAt) {
    }

    public record StudentExam(UUID assignmentId, String title, boolean required, int questionCount, BigDecimal maxPoints, Instant opensAt,
            Instant closesAt, Integer durationMinutes, int maxAttempts, int attemptsUsed, UUID activeAttemptId, boolean canStart,
            List<AttemptSummary> attempts) {
    }
}
