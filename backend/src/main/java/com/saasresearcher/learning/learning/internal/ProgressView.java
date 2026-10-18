package com.saasresearcher.learning.learning.internal;

import java.time.Instant;
import java.util.UUID;

record ProgressView(UUID lessonId, boolean completed, Instant completedAt, long resumeSeconds, long revision, Instant lastActivityAt) {
}
