package com.saasresearcher.learning.grading.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class GradeRows {

    private GradeRows() {
    }

    public enum RevisionStatus { DRAFT, FINALIZED, PUBLISHED, SUPERSEDED }

    public record RevisionRow(UUID id, UUID attemptId, int number, RevisionStatus status, BigDecimal earned, BigDecimal maximum, String reason,
            Instant publishedAt, long version) {

        public boolean isOpen() {
            return status == RevisionStatus.DRAFT || status == RevisionStatus.FINALIZED;
        }
    }

    public record GradeRow(UUID questionId, BigDecimal points, String feedback) {
    }
}
