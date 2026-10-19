package com.saasresearcher.learning.assessments.api;

import java.util.UUID;

/**
 * Called once when an attempt closes, inside the finalizing transaction and while the attempt row is locked.
 * Implemented by the grading module to publish objective-only results automatically.
 */
public interface AttemptFinalizedListener {

    void attemptFinalized(UUID attemptId, AttemptStatus status);
}
