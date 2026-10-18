package com.saasresearcher.learning.identity.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitations")
class Invitation extends OneTimeToken {

    protected Invitation() {
    }

    Invitation(UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        super(userId, tokenHash, expiresAt, now);
    }
}
