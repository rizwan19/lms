package com.saasresearcher.learning.identity.entity;

import com.saasresearcher.learning.identity.api.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** A verified teacher's request for approval; the first decision by another approved teacher resolves it. */
@Getter
@Entity
@Table(name = "teacher_applications")
public class TeacherApplication {

    @Id
    private UUID id;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    private long version;

    protected TeacherApplication() {
    }

    public TeacherApplication(UUID applicantId, Instant now) {
        this.id = UUID.randomUUID();
        this.applicantId = applicantId;
        this.status = ApplicationStatus.PENDING;
        this.createdAt = now;
    }

    public void decide(boolean approve, UUID reviewerId, String reason, Instant now) {
        this.status = approve ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED;
        this.decidedBy = reviewerId;
        this.reason = reason;
        this.decidedAt = now;
    }

    public boolean isPending() {
        return status == ApplicationStatus.PENDING;
    }
}
