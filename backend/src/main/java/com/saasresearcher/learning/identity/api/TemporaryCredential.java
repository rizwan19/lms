package com.saasresearcher.learning.identity.api;

import java.time.Instant;

/** One-time credential shown only to the authorized creator. Never log or persist the plain password. */
public record TemporaryCredential(String username, String temporaryPassword, Instant expiresAt) {

    @Override
    public String toString() {
        return "TemporaryCredential[username=" + username + ", expiresAt=" + expiresAt + "]";
    }
}
