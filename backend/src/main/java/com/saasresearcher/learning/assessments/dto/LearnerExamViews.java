package com.saasresearcher.learning.assessments.dto;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.ExamMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LearnerExamViews {

    private LearnerExamViews() {
    }

    public record AttemptSummary(UUID id, int attemptNumber, AttemptStatus status, Instant finalizedAt) {
    }

    public record LearnerExam(UUID examId, String title, boolean required, ExamMode mode, int questionCount, BigDecimal maxPoints,
            Instant opensAt, Instant closesAt, int durationMinutes, int maxAttempts, int attemptsUsed, UUID activeAttemptId, boolean canStart,
            List<AttemptSummary> attempts) {
    }
}
