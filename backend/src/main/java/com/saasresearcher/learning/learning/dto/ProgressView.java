package com.saasresearcher.learning.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record ProgressView(UUID lessonId, boolean completed, Instant completedAt, long resumeSeconds, long revision, Instant lastActivityAt) {
}
