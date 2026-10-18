package com.saasresearcher.learning.audit.api;

import java.util.Map;
import java.util.UUID;

/**
 * One audit event. Metadata holds safe change details only: never credentials, tokens or raw answers.
 * {@code actorId} is null for anonymous or system actions.
 */
public record AuditEntry(UUID actorId, String action, String aggregateType, UUID aggregateId, AuditOutcome outcome,
        Map<String, Object> metadata) {

    public AuditEntry {
        metadata = Map.copyOf(metadata);
    }

    public static AuditEntry success(UUID actorId, String action, String aggregateType, UUID aggregateId, Map<String, Object> metadata) {
        return new AuditEntry(actorId, action, aggregateType, aggregateId, AuditOutcome.SUCCESS, metadata);
    }

    public static AuditEntry success(UUID actorId, String action, String aggregateType, UUID aggregateId) {
        return success(actorId, action, aggregateType, aggregateId, Map.of());
    }
}
