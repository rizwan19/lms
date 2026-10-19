package com.saasresearcher.learning.assessments.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Author-facing exam responses; these include answer keys and are never shown to students. */
final class ExamViews {

    private ExamViews() {
    }

    record EditionView(UUID versionId, int edition, String title, Instant publishedAt) {
    }

    record DraftHeader(UUID versionId, long revision) {
    }

    record ExamDetail(UUID id, UUID courseId, String title, List<EditionView> editions, DraftHeader draft) {
    }

    /** A draft or an edition with its questions; {@code edition} is null for drafts. */
    record ExamContent(UUID versionId, UUID examId, Integer edition, long revision, String title, String instructions,
            BigDecimal passPercent, List<ExamQuestion> questions) {
    }
}
