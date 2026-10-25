package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.ExamMode;
import com.saasresearcher.learning.assessments.api.ExamSchedule;
import com.saasresearcher.learning.assessments.api.FeedbackPolicy;
import com.saasresearcher.learning.assessments.api.ResultPolicy;
import com.saasresearcher.learning.assessments.dto.ExamViews.ScheduleCommand;
import com.saasresearcher.learning.common.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScheduleRules {

    static final int DEFAULT_DURATION = 60;

    private ScheduleRules() {
    }

    static ExamSchedule validated(ScheduleCommand command, boolean mixed, boolean required) {
        ExamMode mode = command.mode() == null ? ExamMode.SELF_PACED : command.mode();
        ExamSchedule schedule = new ExamSchedule(mode, command.opensAt(), command.closesAt(),
                command.durationMinutes() == null ? DEFAULT_DURATION : command.durationMinutes(),
                command.maxAttempts() == null ? 1 : command.maxAttempts(),
                command.resultPolicy() != null ? command.resultPolicy() : mixed ? ResultPolicy.TEACHER_PUBLISH : ResultPolicy.AUTO_PUBLISH,
                command.feedbackPolicy() == null ? FeedbackPolicy.SCORE_AND_COMMENTS : command.feedbackPolicy());
        require(schedule, mixed, required);
        return schedule;
    }

    static void require(ExamSchedule schedule, boolean mixed, boolean required) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (schedule.mode() == ExamMode.SELF_PACED && (schedule.opensAt() != null || schedule.closesAt() != null)) {
            errors.put("mode", "Self-paced exams have no opening or closing time");
        }
        if (schedule.mode() == ExamMode.SCHEDULED) {
            if (schedule.opensAt() == null) {
                errors.put("opensAt", "A scheduled exam needs an opening time");
            }
            if (schedule.closesAt() == null) {
                errors.put("closesAt", "A scheduled exam needs a closing time");
            } else if (schedule.opensAt() != null && !schedule.closesAt().isAfter(schedule.opensAt())) {
                errors.put("closesAt", "The closing time must be after the opening time");
            }
        }
        if (schedule.durationMinutes() < 1 || schedule.durationMinutes() > 240) {
            errors.put("durationMinutes", "Use 1-240 minutes");
        }
        if (schedule.maxAttempts() < 1 || schedule.maxAttempts() > 10) {
            errors.put("maxAttempts", "Allow 1-10 attempts");
        }
        if (mixed && schedule.resultPolicy() == ResultPolicy.AUTO_PUBLISH) {
            errors.put("resultPolicy", "Exams with written questions must be published by the author");
        }
        if (schedule.feedbackPolicy() == FeedbackPolicy.AFTER_CLOSE && schedule.mode() != ExamMode.SCHEDULED) {
            errors.put("feedbackPolicy", "Showing answers after closing needs a scheduled exam");
        }
        if (schedule.feedbackPolicy() == FeedbackPolicy.AFTER_SUBMISSION && required) {
            errors.put("feedbackPolicy", "Answers right after submission are only for optional practice");
        }
        if (!errors.isEmpty()) {
            throw ApiException.invalid(errors);
        }
    }
}
