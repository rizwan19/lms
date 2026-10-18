package com.saasresearcher.learning.institution.internal;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.institution.api.InstitutionProfile;
import com.saasresearcher.learning.institution.api.InstitutionSettings;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InstitutionService implements InstitutionSettings {

    private final InstitutionSettingsRepository repository;
    private final Clock clock;

    InstitutionService(InstitutionSettingsRepository repository, Clock clock) {
        this.repository = repository;
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
    InstitutionProfile update(String name, ZoneId timezone, long expectedRevision, Actor actor) {
        if (!actor.isAdministrator()) {
            throw ApiException.accessDenied("Only administrators can change institution settings");
        }
        InstitutionSettingsRecord settings = singleton();
        Revisions.require(expectedRevision, settings.version());
        settings.update(name, timezone, clock.instant());
        repository.flush();
        return toProfile(settings);
    }

    private InstitutionSettingsRecord singleton() {
        return repository.findById(InstitutionSettingsRecord.SINGLETON_ID).orElseThrow();
    }

    private static InstitutionProfile toProfile(InstitutionSettingsRecord settings) {
        return new InstitutionProfile(settings.name(), settings.timezone(), settings.version());
    }
}
