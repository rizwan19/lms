package com.saasresearcher.learning.identity.entity;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.identity.api.AccountSummary;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String username;

    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    @Column(name = "password_expires_at")
    private Instant passwordExpiresAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() {
    }

  public   UserAccount(String username, String email, String displayName, Set<Role> roles, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.roles = new HashSet<>(roles);
        this.status = AccountStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void setPassword(String hash, Instant now) {
        this.passwordHash = hash;
        this.passwordChangeRequired = false;
        this.passwordExpiresAt = null;
        this.updatedAt = now;
    }

    public void setTemporaryPassword(String hash, Instant expiresAt, Instant now) {
        this.passwordHash = hash;
        this.passwordChangeRequired = true;
        this.passwordExpiresAt = expiresAt;
        this.updatedAt = now;
    }

    public void updateProfile(String displayName, String email, Set<Role> roles, Instant now) {
        this.displayName = displayName;
        this.email = email;
        this.roles.clear();
        this.roles.addAll(roles);
        this.updatedAt = now;
    }

    public void setStatus(AccountStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    /** Whether the stored password may be used to sign in at {@code now}. */
    public boolean canSignIn(Instant now) {
        return status == AccountStatus.ACTIVE && passwordHash != null
                && (passwordExpiresAt == null || now.isBefore(passwordExpiresAt));
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isStaff() {
        return roles.contains(Role.ADMINISTRATOR) || roles.contains(Role.TEACHER);
    }

    public Actor toActor() {
        return new Actor(id, roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles));
    }

    public AccountSummary toSummary() {
        return new AccountSummary(id, username, displayName, email, Set.copyOf(roles), status);
    }

    public UUID id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public AccountStatus status() {
        return status;
    }

    public boolean passwordChangeRequired() {
        return passwordChangeRequired;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Set<Role> roles() {
        return Set.copyOf(roles);
    }

    public long version() {
        return version;
    }
}
