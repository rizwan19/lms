package com.saasresearcher.learning.institution.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.institution.api.InstitutionProfile;
import com.saasresearcher.learning.institution.api.InstitutionSettings;
import com.saasresearcher.learning.institution.entity.InstitutionSettingsRecord;
import com.saasresearcher.learning.institution.repository.InstitutionSettingsRepository;
import com.saasresearcher.learning.media.api.AssetPurpose;
import com.saasresearcher.learning.media.api.MediaCatalog;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstitutionService implements InstitutionSettings {

    private final InstitutionSettingsRepository repository;
    private final MediaCatalog media;
    private final AuditLog audit;
    private final Clock clock;

    InstitutionService(InstitutionSettingsRepository repository, MediaCatalog media, AuditLog audit, Clock clock) {
        this.repository = repository;
        this.media = media;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public InstitutionProfile get() {
        return toProfile(singleton());
    }

    @Override
    @Transactional
    public void initialize(String name, ZoneId timezone) {
        singleton().update(name, timezone, clock.instant());
    }

    @Override
    @Transactional
    public void lock() {
        repository.lockSingleton().orElseThrow();
    }

    @Transactional
    public InstitutionProfile update(String name, ZoneId timezone, UUID logoAssetId, boolean verification, boolean showsStudent, long expectedRevision,
            Actor actor) {
        if (!actor.isAdministrator()) {
            throw ApiException.accessDenied("Only administrators can change institution settings");
        }
        if (logoAssetId != null && media.requireAvailable(logoAssetId).purpose() != AssetPurpose.BRANDING_IMAGE) {
            throw ApiException.invalid("logoAssetId", "Choose an uploaded branding image");
        }
        InstitutionSettingsRecord settings = singleton();
        Revisions.require(expectedRevision, settings.version());
        settings.update(name, timezone, clock.instant());
        settings.setLogo(logoAssetId, clock.instant());
        settings.setVerification(verification, showsStudent);
        repository.flush();
        audit.record(AuditEntry.success(actor.userId(), "institution.updated", "institution", null));
        return toProfile(settings);
    }

    @Transactional(readOnly = true)
    boolean isLogo(UUID assetId) {
        return assetId.equals(singleton().logoAssetId());
    }

    private InstitutionSettingsRecord singleton() {
        return repository.findById(InstitutionSettingsRecord.SINGLETON_ID).orElseThrow();
    }

    private static InstitutionProfile toProfile(InstitutionSettingsRecord settings) {
        return new InstitutionProfile(settings.name(), settings.timezone(), settings.logoAssetId(), settings.certificateVerification(),
                settings.verificationShowsStudent(), settings.version());
    }
}
