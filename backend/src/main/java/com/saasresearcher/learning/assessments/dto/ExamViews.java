package com.saasresearcher.learning.assessments.dto;

import com.saasresearcher.learning.assessments.entity.ExamQuestion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Author-facing exam responses; these include answer keys and are never shown to students. */
public final class ExamViews {

    private ExamViews() {
    }

    public record EditionView(UUID versionId, int edition, String title, Instant publishedAt) {
    }

    public record DraftHeader(UUID versionId, long revision) {
    }

    public record ExamDetail(UUID id, UUID courseId, String title, List<EditionView> editions, DraftHeader draft) {
    }

    /** A draft or an edition with its questions; {@code edition} is null for drafts. */
    public record ExamContent(UUID versionId, UUID examId, Integer edition, long revision, String title, String instructions,
            BigDecimal passPercent, List<ExamQuestion> questions) {
    }
}
