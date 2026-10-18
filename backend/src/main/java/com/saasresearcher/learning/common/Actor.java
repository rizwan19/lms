package com.saasresearcher.learning.common;

import java.util.Set;
import java.util.UUID;

/** The authenticated caller. Always derived from the session principal, never from request bodies. */
public record Actor(UUID userId, Set<Role> roles) {

    public Actor {
        roles = Set.copyOf(roles);
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }

    public boolean isAdministrator() {
        return has(Role.ADMINISTRATOR);
    }
}
