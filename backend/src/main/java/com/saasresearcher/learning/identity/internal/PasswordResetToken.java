package com.saasresearcher.learning.identity.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken extends OneTimeToken {

    protected PasswordResetToken() {
    }

    PasswordResetToken(UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        super(userId, tokenHash, expiresAt, now);
    }
}
