package com.saasresearcher.learning.reporting.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One learner's progress in a course on their pinned edition. {@code lessonPercent} is null when no lessons are required.
 * {@code inactive} uses the last acknowledged study or exam action, or the grant date if there was none.
 */
public record LearnerProgressRow(UUID enrollmentId, UUID courseVersionId, String displayName, String username, String enrollmentStatus,
        int requiredLessons, int completedRequiredLessons, Integer lessonPercent, int requiredExams, int passedRequiredExams, int pendingGrading,
        Instant lastActivityAt, boolean inactive, String completionStatus) {
}
