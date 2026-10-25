package com.saasresearcher.learning.assessments.dto;

import com.saasresearcher.learning.assessments.api.ExamMode;
import com.saasresearcher.learning.assessments.api.FeedbackPolicy;
import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.api.ResultPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExamViews {

    private ExamViews() {
    }

    public record ScheduleCommand(ExamMode mode, Instant opensAt, Instant closesAt, Integer durationMinutes, Integer maxAttempts,
            ResultPolicy resultPolicy, FeedbackPolicy feedbackPolicy) {
    }

    public record ExamView(UUID id, UUID courseId, UUID courseVersionId, boolean draftEdition, String title, int questionCount,
            BigDecimal maxPoints, boolean mixed, boolean required, ExamMode mode, Instant opensAt, Instant closesAt,
            int durationMinutes, int maxAttempts, ResultPolicy resultPolicy, FeedbackPolicy feedbackPolicy, long revision) {
    }

    public record QuestionContent(UUID id, QuestionType type, String prompt, BigDecimal points, String explanation, String gradingNotes,
            List<OptionContent> options) {

        public QuestionContent {
            options = List.copyOf(options);
        }
    }

    public record OptionContent(UUID id, String label, boolean correct) {
    }

    public record ExamContent(UUID id, UUID courseId, UUID courseVersionId, boolean draftEdition, long revision, String title,
            String instructions, BigDecimal passPercent, List<QuestionContent> questions) {
    }
}
