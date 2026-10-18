package com.saasresearcher.learning.notifications.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable work item. {@code deduplicationKey} (nullable) is globally unique: enqueuing an existing key returns
 * the existing job. Payloads must never contain credentials or tokens.
 */
public record JobRequest(String type, UUID aggregateId, String deduplicationKey, String payloadJson, Instant availableAt) {

    public static JobRequest now(String type, UUID aggregateId, String deduplicationKey, Instant now) {
        return new JobRequest(type, aggregateId, deduplicationKey, "{}", now);
    }
}
