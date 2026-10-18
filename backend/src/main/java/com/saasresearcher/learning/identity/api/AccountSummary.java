package com.saasresearcher.learning.identity.api;

import com.saasresearcher.learning.common.Role;
import java.util.Set;
import java.util.UUID;

public record AccountSummary(UUID id, String username, String displayName, String email, Set<Role> roles, AccountStatus status) {

    public boolean isStaff() {
        return roles.contains(Role.ADMINISTRATOR) || roles.contains(Role.TEACHER);
    }
}
