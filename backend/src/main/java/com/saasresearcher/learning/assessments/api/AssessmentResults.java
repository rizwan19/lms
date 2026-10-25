package com.saasresearcher.learning.assessments.api;

import java.util.List;
import java.util.UUID;

public interface AssessmentResults {

    List<RequiredExamResult> requiredResults(UUID enrollmentId);

    record RequiredExamResult(UUID examId, String title, boolean publishedPass, boolean pendingGrading) {
    }
}
