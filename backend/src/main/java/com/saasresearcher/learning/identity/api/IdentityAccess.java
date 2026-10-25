package com.saasresearcher.learning.identity.api;

import com.saasresearcher.learning.common.Actor;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface IdentityAccess {

    Actor requireActive(UUID userId);

    Map<UUID, AccountSummary> lockShared(Collection<UUID> userIds);

    Map<UUID, AccountSummary> summaries(Collection<UUID> userIds);

    Optional<AccountSummary> findByEmail(String email);
}
