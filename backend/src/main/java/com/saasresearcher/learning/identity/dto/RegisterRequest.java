package com.saasresearcher.learning.identity.dto;

import com.saasresearcher.learning.common.Role;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(@NotNull String username, @NotNull String displayName, @NotNull String email, @NotNull String password,
        @NotNull Role role) {
}
