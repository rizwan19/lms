package com.saasresearcher.learning.enrollment.entity;

import com.saasresearcher.learning.enrollment.api.InvitationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * An author's invitation for one normalized email address. It preauthorizes enrollment for the verified account with
 * that email; the token exists only as a SHA-256 hash.
 */
@Getter
@Entity
@Table(name = "course_invitations")
public class CourseInvitation {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "invited_by", nullable = false, updatable = false)
    private UUID invitedBy;

    @Column(name = "recipient_email", nullable = false, updatable = false)
    private String recipientEmail;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private InvitationStatus status;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    private long version;

    protected CourseInvitation() {
    }

    public CourseInvitation(UUID courseId, UUID invitedBy, String recipientEmail, String tokenHash, Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.courseId = courseId;
        this.invitedBy = invitedBy;
        this.recipientEmail = recipientEmail;
        this.tokenHash = tokenHash;
        this.status = InvitationStatus.PENDING;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return status == InvitationStatus.PENDING && now.isBefore(expiresAt);
    }

    public void accept(UUID learnerId, UUID enrollmentId, Instant now) {
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedBy = learnerId;
        this.enrollmentId = enrollmentId;
        this.acceptedAt = now;
    }

    public void revoke(Instant now) {
        this.status = InvitationStatus.REVOKED;
        this.revokedAt = now;
    }

    public void rotate(String newTokenHash) {
        this.tokenHash = newTokenHash;
    }
}
