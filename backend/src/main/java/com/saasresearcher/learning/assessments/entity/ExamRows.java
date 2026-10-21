package com.saasresearcher.learning.assessments.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ExamRows {

    private ExamRows() {
    }

    public enum VersionStatus { DRAFT, PUBLISHED }

    public record ExamRow(UUID id, UUID courseId, String title) {
    }

    public record VersionRow(UUID id, UUID examId, Integer edition, VersionStatus status, String title, String instructions,
            BigDecimal passPercent, Instant publishedAt, long version) {

        public boolean isDraft() {
            return status == VersionStatus.DRAFT;
        }
    }

    public record ExamSummary(UUID id, String title, Integer latestEdition, UUID latestVersionId, Long draftRevision) {
    }
}
