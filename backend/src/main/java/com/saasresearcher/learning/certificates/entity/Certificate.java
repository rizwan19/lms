package com.saasresearcher.learning.certificates.entity;

import com.saasresearcher.learning.certificates.api.CertificateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * One certificate per completion: PENDING_RENDER → ISSUED, or RENDER_FAILED (retryable); REVOKED by the author or an
 * invalidated completion. The printed fields freeze everything printed at issuance; retries and reinstatement re-render
 * from them. {@code verificationToken} is private: it appears only in the PDF's QR code and is never returned by the API.
 */
@Getter
@Entity
@Table(name = "certificates")
public class Certificate {

    public static final int TEMPLATE_REVISION = 2;

    @Id
    private UUID id;

    @Column(name = "completion_id", nullable = false, updatable = false)
    private UUID completionId;

    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(nullable = false)
    private CertificateStatus status;

    @Column(name = "template_revision", nullable = false, updatable = false)
    private int templateRevision;

    @Column(name = "workspace_name", nullable = false, updatable = false)
    private String workspaceName;

    @Column(name = "logo_asset_id", updatable = false)
    private UUID logoAssetId;

    @Column(name = "learner_name", nullable = false, updatable = false)
    private String learnerName;

    @Column(name = "course_title", nullable = false, updatable = false)
    private String courseTitle;

    @Column(name = "author_name", nullable = false, updatable = false)
    private String authorName;

    @Column(nullable = false, updatable = false)
    private int edition;

    @Column(name = "completed_on", nullable = false, updatable = false)
    private LocalDate completedOn;

    @Column(name = "verification_enabled", nullable = false, updatable = false)
    private boolean verificationEnabled;

    @Column(name = "show_learner_name", nullable = false, updatable = false)
    private boolean showLearnerName;

    @Column(name = "verification_token", updatable = false)
    private String verificationToken;

    @Column(name = "verification_hash", updatable = false)
    private String verificationHash;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(nullable = false)
    private boolean rendered;

    @Column(name = "render_error")
    private String renderError;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Certificate() {
    }

    /** A null {@code verificationToken} turns verification off for this certificate. */
    public Certificate(UUID completionId, UUID learnerId, UUID courseId, String workspaceName, UUID logoAssetId, String learnerName,
            String courseTitle, String authorName, int edition, LocalDate completedOn, boolean showLearnerName, String verificationToken,
            String verificationHash, Instant now) {
        this.id = UUID.randomUUID();
        this.completionId = completionId;
        this.learnerId = learnerId;
        this.courseId = courseId;
        this.status = CertificateStatus.PENDING_RENDER;
        this.templateRevision = TEMPLATE_REVISION;
        this.workspaceName = workspaceName;
        this.logoAssetId = logoAssetId;
        this.learnerName = learnerName;
        this.courseTitle = courseTitle;
        this.authorName = authorName;
        this.edition = edition;
        this.completedOn = completedOn;
        this.verificationEnabled = verificationToken != null;
        this.showLearnerName = showLearnerName;
        this.verificationToken = verificationToken;
        this.verificationHash = verificationHash;
        this.storageKey = "certificates/" + id;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markIssued(Instant now) {
        this.status = CertificateStatus.ISSUED;
        this.rendered = true;
        this.renderError = null;
        this.issuedAt = issuedAt == null ? now : issuedAt;
        this.updatedAt = now;
    }

    /** A render that finished after a revocation: the file exists, the revocation stands. */
    public void markRendered(Instant now) {
        this.rendered = true;
        this.updatedAt = now;
    }

    public void renderFailed(String error, Instant now) {
        this.status = CertificateStatus.RENDER_FAILED;
        this.renderError = error;
        this.updatedAt = now;
    }

    public void queueRender(Instant now) {
        this.status = CertificateStatus.PENDING_RENDER;
        this.renderError = null;
        this.updatedAt = now;
    }

    public void revoke(String reason, UUID revokedBy, Instant now) {
        this.status = CertificateStatus.REVOKED;
        this.revokedReason = reason;
        this.revokedBy = revokedBy;
        this.revokedAt = now;
        this.updatedAt = now;
    }
}
