package com.saasresearcher.learning.assessments.api;

import java.util.UUID;

/** Called inside the transaction that changes an attempt's published result (e.g. to queue completion work). */
public interface PublishedResultListener {

    void resultPublished(UUID enrollmentId, UUID attemptId);
}
