package com.saasresearcher.learning.audit.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditServiceImpl implements AuditService {

    private final JdbcClient jdbc;
    private final EntityManager entityManager;
    private final JsonMapper json;
    private final Clock clock;

    AuditServiceImpl(JdbcClient jdbc, EntityManager entityManager, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void record(AuditEntry entry) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            entityManager.flush();
        }
        jdbc.sql("""
                insert into audit_events (id, occurred_at, actor_id, action, aggregate_type, aggregate_id, outcome, metadata)
                values (:id, :occurredAt, :actorId, :action, :aggregateType, :aggregateId, :outcome, cast(:metadata as jsonb))
                """)
                .param("id", UUID.randomUUID())
                .param("occurredAt", Timestamp.from(clock.instant()))
                .param("actorId", entry.actorId())
                .param("action", entry.action())
                .param("aggregateType", entry.aggregateType())
                .param("aggregateId", entry.aggregateId())
                .param("outcome", entry.outcome().code())
                .param("metadata", json.writeValueAsString(entry.metadata()))
                .update();
    }
}
