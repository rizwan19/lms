package com.saasresearcher.learning.enrollment.entity;

import com.saasresearcher.learning.enrollment.api.AccessRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Append-only history of access request transitions. */
@Getter
@Entity
@Table(name = "course_access_events")
public class AccessEvent {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "from_status", updatable = false)
    private AccessRequestStatus fromStatus;

    @Column(name = "to_status", nullable = false, updatable = false)
    private AccessRequestStatus toStatus;

    @Column(updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccessEvent() {
    }

    public AccessEvent(UUID requestId, UUID actorId, AccessRequestStatus from, AccessRequestStatus to, String reason, Instant now) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.actorId = actorId;
        this.fromStatus = from;
        this.toStatus = to;
        this.reason = reason;
        this.createdAt = now;
    }
}
