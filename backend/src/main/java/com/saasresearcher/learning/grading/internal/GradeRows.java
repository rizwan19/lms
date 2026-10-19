package com.saasresearcher.learning.grading.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class GradeRows {

    private GradeRows() {
    }

    enum RevisionStatus { DRAFT, FINALIZED, PUBLISHED, SUPERSEDED }

    record RevisionRow(UUID id, UUID attemptId, int number, RevisionStatus status, BigDecimal earned, BigDecimal maximum, String reason,
            Instant publishedAt, long version) {

        boolean isOpen() {
            return status == RevisionStatus.DRAFT || status == RevisionStatus.FINALIZED;
        }
    }

    record GradeRow(UUID questionId, BigDecimal points, String feedback) {
    }
}
