package com.saasresearcher.learning.assessments.api;

import java.time.Instant;

public record ExamSchedule(ExamMode mode, Instant opensAt, Instant closesAt, int durationMinutes, int maxAttempts, ResultPolicy resultPolicy,
        FeedbackPolicy feedbackPolicy) {
}
