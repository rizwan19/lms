package com.saasresearcher.learning.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetupRequest(@NotBlank @Size(max = 200) String workspaceName, @NotBlank String timezone, @NotNull String username,
        @NotNull String displayName, @NotNull String email, @NotNull String password) {
}
