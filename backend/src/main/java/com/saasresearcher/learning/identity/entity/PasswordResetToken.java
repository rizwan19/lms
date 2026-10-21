package com.saasresearcher.learning.identity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends OneTimeToken {

    protected PasswordResetToken() {
    }

  public   PasswordResetToken(UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        super(userId, tokenHash, expiresAt, now);
    }
}
