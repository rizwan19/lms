package com.saasresearcher.learning.assessments.api;

import java.time.Instant;
import java.util.UUID;

public record AttemptOutcome(UUID attemptId, AttemptStatus status, String finalizedReason, Instant finalizedAt) {
}
