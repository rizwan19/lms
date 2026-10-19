package com.saasresearcher.learning.assessments.api;

import java.time.Instant;
import java.util.UUID;

/** Acknowledges that the answer is durably stored, not merely received. */
public record AnswerSaved(UUID questionId, long revision, Instant savedAt) {
}
