package com.saasresearcher.learning.assessments.api;

import java.util.UUID;

public interface PublishedResultListener {

    void resultPublished(UUID enrollmentId, UUID attemptId);
}
