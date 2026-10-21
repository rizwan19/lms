package com.saasresearcher.learning.institution.entity;

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
public class InstitutionSettingsRecord {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    @Column(name = "certificate_verification_enabled", nullable = false)
    private boolean certificateVerification;

    @Column(name = "verification_shows_student", nullable = false)
    private boolean verificationShowsStudent;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected InstitutionSettingsRecord() {
    }

    public void update(String name, ZoneId timezone, Instant now) {
        this.name = name.trim();
        this.timezone = timezone.getId();
        this.updatedAt = now;
    }

    public void setLogo(UUID logoAssetId, Instant now) {
        this.logoAssetId = logoAssetId;
        this.updatedAt = now;
    }

    public void setVerification(boolean enabled, boolean showsStudent) {
        this.certificateVerification = enabled;
        this.verificationShowsStudent = showsStudent;
    }

    public boolean certificateVerification() {
        return certificateVerification;
    }

    public boolean verificationShowsStudent() {
        return verificationShowsStudent;
    }

    public UUID logoAssetId() {
        return logoAssetId;
    }

    public String name() {
        return name;
    }

    public ZoneId timezone() {
        return ZoneId.of(timezone);
    }

    public long version() {
        return version;
    }
}
