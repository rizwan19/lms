package com.saasresearcher.learning.assessments.api;

import java.util.UUID;

public interface AttemptFinalizedListener {

    void attemptFinalized(UUID attemptId, boolean needsAuthor);
}
