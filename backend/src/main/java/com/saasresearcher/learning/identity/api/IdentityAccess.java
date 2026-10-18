package com.saasresearcher.learning.identity.api;

import com.saasresearcher.learning.common.Actor;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface IdentityAccess {

    /** Returns the actor for an active account; unknown or disabled accounts are rejected. */
    Actor requireActive(UUID userId);

    Map<UUID, AccountSummary> summaries(Collection<UUID> userIds);

    Optional<AccountSummary> findByUsername(String username);

    Optional<AccountSummary> findByEmail(String email);
}
