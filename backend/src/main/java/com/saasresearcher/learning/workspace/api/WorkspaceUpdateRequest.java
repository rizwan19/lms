package com.saasresearcher.learning.workspace.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record WorkspaceUpdateRequest(@NotBlank @Size(max = 200) String name, @NotBlank String timezone, UUID logoAssetId, boolean certificateVerification,
        boolean verificationShowsStudent, long expectedRevision) {
}
