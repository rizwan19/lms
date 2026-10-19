package com.saasresearcher.learning.assessments.api;

import java.util.List;
import java.util.UUID;

public interface AssessmentResults {

    /**
     * One entry per required assignment of the enrollment's class. Only published results can pass; the highest
     * published score counts, so any published pass satisfies the requirement.
     */
    List<RequiredExamResult> requiredResults(UUID enrollmentId);

    record RequiredExamResult(UUID assignmentId, String title, boolean publishedPass, boolean pendingGrading) {
    }
}
