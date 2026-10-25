package com.saasresearcher.learning.reporting.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Best published score per course exam of one edition; unpublished and pending grades never appear as scores. */
public record LearnerScoreRow(UUID enrollmentId, String displayName, String username, List<Score> scores) {

    public record Score(UUID examId, String examTitle, boolean required, BigDecimal bestEarned, BigDecimal maximum, Boolean passed,
            UUID publishedRevisionId, boolean pendingGrading) {
    }
}
