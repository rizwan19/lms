package com.saasresearcher.learning.assessments.internal;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.QuestionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence shapes of attempts; snapshots include the private key and never leave this module as-is. */
final class AttemptRows {

    private AttemptRows() {
    }

    enum FinalizedReason { SUBMITTED, EXPIRED }

    record AttemptRow(UUID id, UUID assignmentId, UUID enrollmentId, UUID studentId, int attemptNumber, AttemptStatus status,
            Instant startedAt, Instant expiresAt, Instant finalizedAt, FinalizedReason finalizedReason, BigDecimal objectiveScore,
            BigDecimal maxScore) {

        boolean isExpired(Instant now) {
            return status == AttemptStatus.IN_PROGRESS && !now.isBefore(expiresAt);
        }
    }

    record SnapshotOption(UUID id, String label, boolean correct) {
    }

    record SnapshotQuestion(UUID questionId, int position, QuestionType type, String prompt, BigDecimal points, String explanation,
            String gradingNotes, List<SnapshotOption> options, BigDecimal autoPoints) {
    }

    record Answer(UUID questionId, List<UUID> selectedOptionIds, String writtenText, long revision, Instant savedAt) {
    }
}
