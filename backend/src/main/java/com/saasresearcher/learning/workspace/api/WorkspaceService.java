package com.saasresearcher.learning.workspace.api;

import com.saasresearcher.learning.common.Actor;

import java.time.ZoneId;
import java.util.UUID;

public interface WorkspaceService {

    WorkspaceData getWorkspaceData();

    void lockWorkspace();

    void beginSetup(String name, ZoneId timezone, UUID ownerId);

    void activate();

    WorkspaceData update(WorkspaceUpdateRequest request, Actor actor);

    boolean isLogo(UUID assetId);
}
