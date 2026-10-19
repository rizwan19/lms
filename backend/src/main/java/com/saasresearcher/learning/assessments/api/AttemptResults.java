package com.saasresearcher.learning.assessments.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bridge between attempts and grading: grading reads snapshots and writes the published projection here. */
public interface AttemptResults {

    GradingSnapshot snapshot(UUID attemptId);

    /** Records a newly published grade revision on the attempt; pass/fail compares the exact percentage before rounding. */
    void recordPublished(UUID attemptId, UUID gradeRevisionId, BigDecimal earned, BigDecimal maximum, Instant publishedAt);

    /** Whether keys and explanations may be shown to the student now, per the assignment's feedback policy. */
    boolean keysReleased(UUID attemptId);

    /** The student's finalized attempts with their published projection (null fields while unpublished). */
    List<OwnResult> resultsOf(UUID studentId);

    record OwnResult(UUID attemptId, UUID enrollmentId, String examTitle, boolean required, int attemptNumber, AttemptStatus status,
            Instant finalizedAt, BigDecimal publishedEarned, BigDecimal publishedMaximum, Boolean publishedPassed, Instant publishedAt) {
    }
}
