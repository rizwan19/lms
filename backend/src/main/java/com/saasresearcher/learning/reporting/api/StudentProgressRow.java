package com.saasresearcher.learning.reporting.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One student's progress in a class. {@code lessonPercent} is null when no lessons are required. {@code inactive} uses
 * the last acknowledged study or exam action, or the enrollment date if there was none.
 */
public record StudentProgressRow(UUID enrollmentId, String displayName, String username, String enrollmentStatus, int requiredLessons,
        int completedRequiredLessons, Integer lessonPercent, int requiredExams, int passedRequiredExams, int pendingGrading,
        Instant lastActivityAt, boolean inactive, String completionStatus) {
}
