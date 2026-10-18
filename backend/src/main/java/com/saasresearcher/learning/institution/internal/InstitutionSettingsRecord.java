package com.saasresearcher.learning.institution.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "institution_settings")
class InstitutionSettingsRecord {

    static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected InstitutionSettingsRecord() {
    }

    void update(String name, ZoneId timezone, Instant now) {
        this.name = name.trim();
        this.timezone = timezone.getId();
        this.updatedAt = now;
    }

    void setLogo(UUID logoAssetId, Instant now) {
        this.logoAssetId = logoAssetId;
        this.updatedAt = now;
    }

    UUID logoAssetId() {
        return logoAssetId;
    }

    String name() {
        return name;
    }

    ZoneId timezone() {
        return ZoneId.of(timezone);
    }

    long version() {
        return version;
    }
}
