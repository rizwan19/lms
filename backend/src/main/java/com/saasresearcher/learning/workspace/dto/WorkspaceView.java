package com.saasresearcher.learning.workspace.dto;

import com.saasresearcher.learning.workspace.api.WorkspaceData;
import com.saasresearcher.learning.workspace.api.WorkspaceState;
import java.util.UUID;

/** Public branding and settings; readable before sign-in. */
public record WorkspaceView(String name, String timezone, WorkspaceState state, UUID logoAssetId, boolean certificateVerification,
        boolean verificationShowsStudent, long revision) {

    public static WorkspaceView of(WorkspaceData data) {
        return new WorkspaceView(data.name(), data.timezone().getId(), data.state(), data.logoAssetId(), data.certificateVerification(),
                data.verificationShowsStudent(), data.revision());
    }
}
