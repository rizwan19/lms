package com.saasresearcher.learning.workspace.entity;

import com.saasresearcher.learning.workspace.api.WorkspaceState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "workspace")
public class Workspace {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    @Column(nullable = false)
    private String timezone;

    @Column(nullable = false)
    private WorkspaceState state;

    @Column(name = "created_by")
    private UUID createdBy;

    @Setter
    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    @Setter
    @Column(name = "public_verification", nullable = false)
    private boolean certificateVerification;

    @Setter
    @Column(name = "certificate_show_name", nullable = false)
    private boolean certificateShowName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public void update(String name, ZoneId timezone, Instant now) {
        this.name = name.trim();
        this.timezone = timezone.getId();
        this.updatedAt = now;
    }

    public void beginSetup(UUID ownerId, Instant now) {
        this.createdBy = ownerId;
        this.state = WorkspaceState.OWNER_VERIFICATION_PENDING;
        this.updatedAt = now;
    }

    public void activate(Instant now) {
        this.state = WorkspaceState.ACTIVE;
        this.updatedAt = now;
    }

    public boolean verificationShowsStudent() {
        return certificateShowName;
    }

    public ZoneId timezone() {
        return ZoneId.of(timezone);
    }
}
