package com.saasresearcher.learning.grading.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PublishedResult(UUID attemptId, UUID gradeRevisionId, int revisionNumber, BigDecimal earned, BigDecimal maximum, Instant publishedAt) {
}
