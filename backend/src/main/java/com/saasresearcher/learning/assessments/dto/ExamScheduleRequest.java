package com.saasresearcher.learning.assessments.dto;

import com.saasresearcher.learning.assessments.api.ExamMode;
import com.saasresearcher.learning.assessments.api.FeedbackPolicy;
import com.saasresearcher.learning.assessments.api.ResultPolicy;
import com.saasresearcher.learning.assessments.dto.ExamViews.ScheduleCommand;
import java.time.Instant;

public record ExamScheduleRequest(long expectedRevision, Boolean required, ExamMode mode, Instant opensAt, Instant closesAt,
                                  Integer durationMinutes, Integer maxAttempts, ResultPolicy resultPolicy, FeedbackPolicy feedbackPolicy) {

    public ScheduleCommand schedule() {
        return new ScheduleCommand(mode, opensAt, closesAt, durationMinutes, maxAttempts, resultPolicy, feedbackPolicy);
    }
}
