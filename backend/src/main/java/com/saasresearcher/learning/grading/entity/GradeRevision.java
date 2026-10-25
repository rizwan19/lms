package com.saasresearcher.learning.grading.entity;

import com.saasresearcher.learning.grading.api.RevisionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Grade revisions per attempt: DRAFT → FINALIZED → PUBLISHED, later SUPERSEDED by a regrade's publication. */
@Getter
@Entity
@Table(name = "grade_revisions")
public class GradeRevision {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(name = "revision_number", nullable = false, updatable = false)
    private int number;

    @Column(nullable = false)
    private RevisionStatus status;

    private BigDecimal earned;

    @Column(nullable = false, updatable = false)
    private BigDecimal maximum;

    @Column(updatable = false)
    private String reason;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Version
    private long version;

    protected GradeRevision() {
    }

    public GradeRevision(UUID attemptId, int number, BigDecimal maximum, String reason, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.attemptId = attemptId;
        this.number = number;
        this.status = RevisionStatus.DRAFT;
        this.maximum = maximum;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public boolean isOpen() {
        return status == RevisionStatus.DRAFT || status == RevisionStatus.FINALIZED;
    }

    /** Any grade change reopens a finalized revision. */
    public void changed() {
        this.status = RevisionStatus.DRAFT;
        this.earned = null;
        this.finalizedAt = null;
    }

    public void finalizeWith(BigDecimal earned, Instant now) {
        this.status = RevisionStatus.FINALIZED;
        this.earned = earned;
        this.finalizedAt = now;
    }

    public void publish(UUID publishedBy, Instant now) {
        this.status = RevisionStatus.PUBLISHED;
        this.publishedBy = publishedBy;
        this.publishedAt = now;
    }

    public void supersede() {
        this.status = RevisionStatus.SUPERSEDED;
    }
}
