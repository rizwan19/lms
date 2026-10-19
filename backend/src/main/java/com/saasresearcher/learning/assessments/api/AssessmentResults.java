package com.saasresearcher.learning.assessments.api;

import java.util.List;
import java.util.UUID;

public interface AssessmentResults {

    /** One entry per required assignment of the enrollment's class; only published results can pass. */
    List<RequiredExamResult> requiredResults(UUID enrollmentId);

    record RequiredExamResult(UUID assignmentId, boolean publishedPass) {
    }
}
