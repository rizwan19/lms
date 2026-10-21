package com.saasresearcher.learning.operations.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.operations.api.MaintenanceMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the maintenance switch at most once per second per instance; scripts may flip it directly in the database. */
@Service
public class MaintenanceService implements MaintenanceMode {

    private static final long CACHE_MILLIS = 1000;

    private final JdbcClient jdbc;
    private final AuditLog audit;
    private final Clock clock;
    private volatile boolean active;
    private volatile long checkedAt;

    MaintenanceService(JdbcClient jdbc, AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public boolean isActive() {
        long now = System.currentTimeMillis();
        if (now - checkedAt > CACHE_MILLIS) {
            active = jdbc.sql("select maintenance from system_state where id = 1").query(Boolean.class).single();
            checkedAt = now;
        }
        return active;
    }

    public record State(boolean active, String reason, Instant updatedAt) {
    }

    public State state() {
        return jdbc.sql("select maintenance, reason, updated_at from system_state where id = 1")
                .query((rs, row) -> new State(rs.getBoolean("maintenance"), rs.getString("reason"), rs.getTimestamp("updated_at").toInstant()))
                .single();
    }

    @Transactional
    public State set(boolean on, String reason, Actor actor) {
        if (!actor.isAdministrator()) {
            throw ApiException.accessDenied("Administrator role required");
        }
        jdbc.sql("update system_state set maintenance = :on, reason = :reason, updated_at = :now where id = 1")
                .param("on", on).param("reason", reason).param("now", Timestamp.from(clock.instant())).update();
        checkedAt = 0;
        audit.record(AuditEntry.success(actor.userId(), on ? "maintenance.started" : "maintenance.ended", "system", null,
                Map.of("reason", reason == null ? "" : reason)));
        return state();
    }
}
