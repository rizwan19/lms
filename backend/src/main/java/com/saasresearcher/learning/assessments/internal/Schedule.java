package com.saasresearcher.learning.assessments.internal;

import com.saasresearcher.learning.common.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Assignment schedule and policies (spec §11). Duration null means untimed, which needs a closing time; objective-only
 * exams default to automatic publication, mixed exams must be teacher-published.
 */
record Schedule(Instant opensAt, Instant closesAt, Integer durationMinutes, int maxAttempts, ResultPolicy resultPolicy,
        FeedbackPolicy feedbackPolicy) {

    enum ResultPolicy { AUTO_PUBLISH, TEACHER_PUBLISH }

    enum FeedbackPolicy { SCORE_AND_COMMENTS, AFTER_CLOSE, AFTER_SUBMISSION }

    static final int DEFAULT_DURATION = 60;

    static Schedule defaults(Instant opensAt, Instant closesAt, boolean mixed) {
        return new Schedule(opensAt, closesAt, DEFAULT_DURATION, 1, mixed ? ResultPolicy.TEACHER_PUBLISH : ResultPolicy.AUTO_PUBLISH,
                FeedbackPolicy.SCORE_AND_COMMENTS);
    }

    /** Fills omitted policies with defaults and validates the combination. */
    static Schedule validated(Instant opensAt, Instant closesAt, Integer durationMinutes, Integer maxAttempts, ResultPolicy resultPolicy,
            FeedbackPolicy feedbackPolicy, boolean mixed, boolean required) {
        Schedule schedule = new Schedule(opensAt, closesAt, durationMinutes, maxAttempts == null ? 1 : maxAttempts,
                resultPolicy != null ? resultPolicy : mixed ? ResultPolicy.TEACHER_PUBLISH : ResultPolicy.AUTO_PUBLISH,
                feedbackPolicy == null ? FeedbackPolicy.SCORE_AND_COMMENTS : feedbackPolicy);
        Map<String, String> errors = new LinkedHashMap<>();
        if (durationMinutes != null && (durationMinutes < 1 || durationMinutes > 240)) {
            errors.put("durationMinutes", "Use 1-240 minutes");
        }
        if (durationMinutes == null && closesAt == null) {
            errors.put("closesAt", "An untimed exam needs a closing time");
        }
        if (opensAt != null && closesAt != null && !closesAt.isAfter(opensAt)) {
            errors.put("closesAt", "The closing time must be after the opening time");
        }
        if (schedule.maxAttempts() < 1 || schedule.maxAttempts() > 10) {
            errors.put("maxAttempts", "Allow 1-10 attempts");
        }
        if (mixed && schedule.resultPolicy() == ResultPolicy.AUTO_PUBLISH) {
            errors.put("resultPolicy", "Exams with written questions must be published by a teacher");
        }
        if (schedule.feedbackPolicy() == FeedbackPolicy.AFTER_CLOSE && closesAt == null) {
            errors.put("feedbackPolicy", "Showing answers after closing needs a closing time");
        }
        if (schedule.feedbackPolicy() == FeedbackPolicy.AFTER_SUBMISSION && required) {
            errors.put("feedbackPolicy", "Answers right after submission are only for optional practice");
        }
        if (!errors.isEmpty()) {
            throw ApiException.invalid(errors);
        }
        return schedule;
    }
}
