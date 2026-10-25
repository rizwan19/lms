package com.saasresearcher.learning.workspace.service;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.media.api.AssetAccessPolicy;
import com.saasresearcher.learning.media.api.AssetReferences;
import java.util.UUID;

import com.saasresearcher.learning.workspace.api.WorkspaceService;
import org.springframework.stereotype.Component;

@Component
public class LogoAssetLinks implements AssetAccessPolicy, AssetReferences {

    private final WorkspaceService workspaceService;

    LogoAssetLinks(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Override
    public boolean canRead(UUID assetId, Actor actor) {
        return workspaceService.isLogo(assetId);
    }

    @Override
    public boolean isReferenced(UUID assetId) {
        return workspaceService.isLogo(assetId);
    }
}
