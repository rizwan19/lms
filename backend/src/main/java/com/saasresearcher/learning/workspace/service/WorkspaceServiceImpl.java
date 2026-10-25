package com.saasresearcher.learning.workspace.service;

import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.common.Timezones;
import com.saasresearcher.learning.workspace.api.WorkspaceData;
import com.saasresearcher.learning.workspace.api.WorkspaceService;
import com.saasresearcher.learning.workspace.api.WorkspaceUpdateRequest;
import com.saasresearcher.learning.workspace.entity.Workspace;
import com.saasresearcher.learning.workspace.repository.WorkspaceRepository;
import com.saasresearcher.learning.media.api.AssetPurpose;
import com.saasresearcher.learning.media.api.MediaCatalog;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final MediaCatalog mediaCatalog;
    private final AuditService auditService;
    private final Clock clock;
    private final RowLocks locks;

    @Override
    @Transactional(readOnly = true)
    public WorkspaceData getWorkspaceData() {
        return toData(getWorkspace());
    }

    @Override
    @Transactional
    public void beginSetup(String name, ZoneId timezone, UUID ownerId) {
        Workspace settings = getWorkspace();
        settings.update(name, timezone, clock.instant());
        settings.beginSetup(ownerId, clock.instant());
        workspaceRepository.flush();
    }

    @Override
    @Transactional
    public void activate() {
        getWorkspace().activate(clock.instant());
        workspaceRepository.flush();
    }

    @Override
    @Transactional
    public void lockWorkspace() {
        locks.forUpdate(getWorkspace());
    }

    @Override
    @Transactional
    public WorkspaceData update(WorkspaceUpdateRequest request, Actor actor) {
        actor.requireApprovedTeacher();
        ZoneId timezone = Timezones.parse("timezone", request.timezone());
        UUID logoAssetId = request.logoAssetId();
        if (logoAssetId != null && mediaCatalog.requireAvailable(logoAssetId).purpose() != AssetPurpose.BRANDING_IMAGE) {
            throw ApiException.invalid("logoAssetId", "Choose an uploaded branding image");
        }
        Workspace workspace = getWorkspace();
        Revisions.require(request.expectedRevision(), workspace.getVersion());
        workspace.setName(request.name().trim());
        workspace.setTimezone(timezone.getId());
        workspace.setLogoAssetId(logoAssetId);
        workspace.setCertificateVerification(request.certificateVerification());
        workspace.setCertificateShowName(request.verificationShowsStudent());
        workspace.setUpdatedAt(clock.instant());
        workspaceRepository.save(workspace);
        auditService.record(AuditEntry.success(actor.userId(), "workspace.updated", "workspace", null));
        return toData(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLogo(UUID assetId) {
        return assetId.equals(getWorkspace().getLogoAssetId());
    }

    private Workspace getWorkspace() {
        return workspaceRepository.findById(Workspace.SINGLETON_ID).orElseThrow();
    }

    private static WorkspaceData toData(Workspace workspace) {
        return new WorkspaceData(workspace.getName(), workspace.timezone(), workspace.getState(), workspace.getCreatedBy(), workspace.getLogoAssetId(),
                workspace.isCertificateVerification(), workspace.verificationShowsStudent(), workspace.getVersion());
    }
}
