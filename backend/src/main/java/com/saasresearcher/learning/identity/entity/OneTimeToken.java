package com.saasresearcher.learning.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;

/** Single-use token stored only as a SHA-256 hash. */
@MappedSuperclass
public abstract class OneTimeToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OneTimeToken() {
    }

    protected OneTimeToken(UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    /** Replaces the stored hash so only the most recently delivered link works. */
    public void rotate(String newTokenHash) {
        this.tokenHash = newTokenHash;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
