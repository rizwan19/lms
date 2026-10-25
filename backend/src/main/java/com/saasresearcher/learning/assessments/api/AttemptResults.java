package com.saasresearcher.learning.assessments.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AttemptResults {

    GradingSnapshot snapshot(UUID attemptId);

    void recordPublished(UUID attemptId, UUID gradeRevisionId, BigDecimal earned, BigDecimal maximum, Instant publishedAt);

    boolean keysReleased(UUID attemptId);

    List<OwnResult> resultsOf(UUID learnerId);

    record OwnResult(UUID attemptId, UUID enrollmentId, UUID courseId, String examTitle, boolean required, int attemptNumber,
            AttemptStatus status, Instant finalizedAt, BigDecimal publishedEarned, BigDecimal publishedMaximum, Boolean publishedPassed,
            Instant publishedAt) {
    }
}
