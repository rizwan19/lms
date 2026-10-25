package com.saasresearcher.learning.workspace.api;

import java.time.ZoneId;
import java.util.UUID;

public record WorkspaceData(String name, ZoneId timezone, WorkspaceState state, UUID ownerId, UUID logoAssetId, boolean certificateVerification,
        boolean verificationShowsStudent, long revision) {
}
