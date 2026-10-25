package com.saasresearcher.learning.identity.entity;

import com.saasresearcher.learning.identity.api.TokenPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Single-use token bound to one purpose and stored only as a SHA-256 hash. */
@Getter
@Entity
@Table(name = "identity_tokens")
public class IdentityToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private TokenPurpose purpose;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected IdentityToken() {
    }

    public IdentityToken(UUID userId, TokenPurpose purpose, String tokenHash, Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null && consumedAt == null) {
            this.revokedAt = now;
        }
    }

    /** Replaces the stored hash so only the most recently delivered link works. */
    public void rotate(String newTokenHash) {
        this.tokenHash = newTokenHash;
    }
}
