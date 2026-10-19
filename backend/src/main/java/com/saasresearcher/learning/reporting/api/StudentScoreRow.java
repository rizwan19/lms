package com.saasresearcher.learning.reporting.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Best published score per assignment; unpublished and pending grades never appear as scores. */
public record StudentScoreRow(UUID enrollmentId, String displayName, String username, List<Score> scores) {

    public record Score(UUID assignmentId, String examTitle, boolean required, BigDecimal bestEarned, BigDecimal maximum, Boolean passed,
            UUID publishedRevisionId, boolean pendingGrading) {
    }
}
