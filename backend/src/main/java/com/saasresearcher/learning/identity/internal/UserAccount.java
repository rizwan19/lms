package com.saasresearcher.learning.identity.internal;

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
class UserAccount {

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

    UserAccount(String username, String email, String displayName, Set<Role> roles, UUID createdBy, Instant now) {
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

    void setPassword(String hash, Instant now) {
        this.passwordHash = hash;
        this.passwordChangeRequired = false;
        this.passwordExpiresAt = null;
        this.updatedAt = now;
    }

    void setTemporaryPassword(String hash, Instant expiresAt, Instant now) {
        this.passwordHash = hash;
        this.passwordChangeRequired = true;
        this.passwordExpiresAt = expiresAt;
        this.updatedAt = now;
    }

    void updateProfile(String displayName, String email, Set<Role> roles, Instant now) {
        this.displayName = displayName;
        this.email = email;
        this.roles.clear();
        this.roles.addAll(roles);
        this.updatedAt = now;
    }

    void setStatus(AccountStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    /** Whether the stored password may be used to sign in at {@code now}. */
    boolean canSignIn(Instant now) {
        return status == AccountStatus.ACTIVE && passwordHash != null
                && (passwordExpiresAt == null || now.isBefore(passwordExpiresAt));
    }

    boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    boolean isStaff() {
        return roles.contains(Role.ADMINISTRATOR) || roles.contains(Role.TEACHER);
    }

    Actor toActor() {
        return new Actor(id, roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles));
    }

    AccountSummary toSummary() {
        return new AccountSummary(id, username, displayName, email, Set.copyOf(roles), status);
    }

    UUID id() {
        return id;
    }

    String username() {
        return username;
    }

    String email() {
        return email;
    }

    String displayName() {
        return displayName;
    }

    String passwordHash() {
        return passwordHash;
    }

    AccountStatus status() {
        return status;
    }

    boolean passwordChangeRequired() {
        return passwordChangeRequired;
    }

    UUID createdBy() {
        return createdBy;
    }

    Instant createdAt() {
        return createdAt;
    }

    Set<Role> roles() {
        return Set.copyOf(roles);
    }

    long version() {
        return version;
    }
}
