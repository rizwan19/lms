package com.saasresearcher.learning.completion.api;

import com.saasresearcher.learning.assessments.api.AssessmentResults.RequiredExamResult;
import com.saasresearcher.learning.learning.api.LessonSummary;
import java.util.List;
import java.util.UUID;

/** Eligibility breakdown of one enrollment; {@code completionId} is null until first achieved. */
public record CompletionState(UUID enrollmentId, CompletionStatus status, UUID completionId, LessonSummary lessons,
        List<RequiredExamResult> exams) {
}
