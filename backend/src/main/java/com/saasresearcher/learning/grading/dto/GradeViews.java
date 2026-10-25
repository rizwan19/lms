package com.saasresearcher.learning.grading.dto;

import com.saasresearcher.learning.assessments.api.GradingSnapshot;
import com.saasresearcher.learning.grading.api.RevisionStatus;
import com.saasresearcher.learning.grading.entity.GradeRevision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Author-facing grading shapes; they include keys and private grades. */
public final class GradeViews {

    private GradeViews() {
    }

    public record RevisionView(UUID id, int number, RevisionStatus status, BigDecimal earned, BigDecimal maximum, String reason, Instant publishedAt,
            long version) {

        public static RevisionView of(GradeRevision revision) {
            return new RevisionView(revision.getId(), revision.getNumber(), revision.getStatus(), revision.getEarned(), revision.getMaximum(), revision.getReason(),
                    revision.getPublishedAt(), revision.getVersion());
        }
    }

    public record GradeRow(UUID questionId, BigDecimal points, String feedback) {
    }

    public record GradeView(GradingSnapshot attempt, RevisionView revision, List<GradeRow> grades, List<RevisionView> history) {
    }

    public record QueueView(UUID attemptId, UUID courseVersionId, String learnerName, String username, String examTitle, int attemptNumber,
            Instant finalizedAt, RevisionStatus gradeStatus, int revisionNumber, BigDecimal publishedEarned, BigDecimal maximum) {
    }

    public record GradeCommand(long expectedRevision, BigDecimal points, String feedback) {
    }
}
