package com.saasresearcher.learning.completion.api;

import java.util.UUID;

public interface CompletionService {

    /** Recomputes eligibility under the enrollment lock; idempotent, so duplicate jobs are harmless. */
    CompletionState reconcile(UUID enrollmentId);

    CompletionSnapshot get(UUID completionId);
}
