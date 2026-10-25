package com.saasresearcher.learning.enrollment.entity;

import com.saasresearcher.learning.enrollment.api.AccessRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Current request state per course and learner; every transition is appended to {@link AccessEvent}. */
@Getter
@Entity
@Table(name = "course_access_requests")
public class AccessRequest {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;

    @Column(nullable = false)
    private AccessRequestStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    private String reason;

    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    private long version;

    protected AccessRequest() {
    }

    public AccessRequest(UUID courseId, UUID learnerId, Instant now) {
        this.id = UUID.randomUUID();
        this.courseId = courseId;
        this.learnerId = learnerId;
        this.status = AccessRequestStatus.PENDING;
        this.requestedAt = now;
    }

    /** Opens the row again for a new request after a rejection or cancellation. */
    public void reopen(Instant now) {
        this.status = AccessRequestStatus.PENDING;
        this.decidedBy = null;
        this.decidedAt = null;
        this.reason = null;
        this.enrollmentId = null;
        this.requestedAt = now;
    }

    public void approve(UUID authorId, UUID enrollmentId, Instant now) {
        resolve(AccessRequestStatus.APPROVED, authorId, null, now);
        this.enrollmentId = enrollmentId;
    }

    public void reject(UUID authorId, String reason, Instant now) {
        resolve(AccessRequestStatus.REJECTED, authorId, reason, now);
    }

    public void cancel(UUID learnerId, Instant now) {
        resolve(AccessRequestStatus.CANCELLED, learnerId, null, now);
    }

    private void resolve(AccessRequestStatus status, UUID by, String reason, Instant now) {
        this.status = status;
        this.decidedBy = by;
        this.reason = reason;
        this.decidedAt = now;
    }

    public boolean isPending() {
        return status == AccessRequestStatus.PENDING;
    }
}
