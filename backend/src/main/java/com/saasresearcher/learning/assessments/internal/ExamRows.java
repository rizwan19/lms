package com.saasresearcher.learning.assessments.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class ExamRows {

    private ExamRows() {
    }

    enum VersionStatus { DRAFT, PUBLISHED }

    record ExamRow(UUID id, UUID courseId, String title) {
    }

    record VersionRow(UUID id, UUID examId, Integer edition, VersionStatus status, String title, String instructions,
            BigDecimal passPercent, Instant publishedAt, long version) {

        boolean isDraft() {
            return status == VersionStatus.DRAFT;
        }
    }

    record ExamSummary(UUID id, String title, Integer latestEdition, UUID latestVersionId, Long draftRevision) {
    }
}
