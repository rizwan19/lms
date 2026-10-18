package com.saasresearcher.learning.learning.api;

import java.time.Instant;

/** Required-lesson progress of one enrollment; {@code lastActivityAt} is null before any acknowledged study action. */
public record LessonSummary(int required, int completed, Instant lastActivityAt) {
}
