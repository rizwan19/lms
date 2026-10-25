package com.saasresearcher.learning.enrollment.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Lets higher modules veto new access grants for a course edition (e.g. a required scheduled exam that has already
 * closed). Owned by enrollment and implemented by the module that owns the rule; runs inside the grant transaction.
 */
public interface GrantPolicy {

    /** An error code and message when no new learner may be granted the edition now. */
    Optional<Blocker> blocker(UUID courseVersionId);

    record Blocker(String code, String message) {
    }
}
