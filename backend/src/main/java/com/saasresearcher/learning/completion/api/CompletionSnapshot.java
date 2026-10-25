package com.saasresearcher.learning.completion.api;

import java.time.Instant;
import java.util.UUID;

/** Stable facts of a completion. Branding and names are snapshotted by certificates, not here. */
public record CompletionSnapshot(UUID id, UUID enrollmentId, UUID learnerId, UUID courseId, UUID courseVersionId, CompletionStatus status,
        Instant achievedAt) {
}
