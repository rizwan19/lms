package com.saasresearcher.learning.assessments.api;

import java.time.Instant;
import java.util.UUID;

public record AnswerSaved(UUID questionId, Instant savedAt) {
}
