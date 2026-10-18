package com.saasresearcher.learning.identity.api;

import java.time.Instant;
import java.util.UUID;

/** A new student account: invited by email ({@code invitationExpiresAt}) or given a one-time {@code temporaryCredential}. */
public record ProvisionedStudent(UUID id, TemporaryCredential temporaryCredential, Instant invitationExpiresAt) {
}
