package com.saasresearcher.learning.assessments.entity;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.QuestionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence shapes of attempts; snapshots include the private key and never leave this module as-is. */
public final class AttemptRows {

    private AttemptRows() {
    }

    public enum FinalizedReason { SUBMITTED, EXPIRED }

    public record AttemptRow(UUID id, UUID assignmentId, UUID enrollmentId, UUID studentId, int attemptNumber, AttemptStatus status,
            Instant startedAt, Instant expiresAt, Instant finalizedAt, FinalizedReason finalizedReason, BigDecimal objectiveScore,
            BigDecimal maxScore) {

        public boolean isExpired(Instant now) {
            return status == AttemptStatus.IN_PROGRESS && !now.isBefore(expiresAt);
        }
    }

    public record SnapshotOption(UUID id, String label, boolean correct) {
    }

    public record SnapshotQuestion(UUID questionId, int position, QuestionType type, String prompt, BigDecimal points, String explanation,
            String gradingNotes, List<SnapshotOption> options, BigDecimal autoPoints) {
    }

    public record Answer(UUID questionId, List<UUID> selectedOptionIds, String writtenText, long revision, Instant savedAt) {
    }
}
