package com.saasresearcher.learning.enrollment.dto;

import com.saasresearcher.learning.enrollment.api.AccessRequestStatus;
import com.saasresearcher.learning.enrollment.api.EnrollmentStatus;
import com.saasresearcher.learning.enrollment.api.GrantSource;
import com.saasresearcher.learning.enrollment.api.InvitationStatus;
import com.saasresearcher.learning.enrollment.entity.AccessRequest;
import com.saasresearcher.learning.enrollment.entity.CourseEnrollment;
import com.saasresearcher.learning.enrollment.entity.CourseInvitation;
import com.saasresearcher.learning.identity.api.AccountSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response shapes for requests, invitations and rosters. No tokens ever appear here. */
public final class EnrollmentViews {

    private EnrollmentViews() {
    }

    public record RosterRow(UUID enrollmentId, UUID learnerId, String username, String displayName, EnrollmentStatus status,
            GrantSource grantSource, UUID courseVersionId, Instant grantedAt, Instant statusChangedAt, long revision) {

        public static RosterRow of(CourseEnrollment enrollment, AccountSummary learner) {
            return new RosterRow(enrollment.getId(), enrollment.getLearnerId(), learner.username(), learner.displayName(), enrollment.getStatus(),
                    enrollment.getGrantSource(), enrollment.getCourseVersionId(), enrollment.getGrantedAt(), enrollment.getStatusChangedAt(),
                    enrollment.getVersion());
        }
    }

    public record MyEnrollment(UUID enrollmentId, UUID courseId, String courseTitle, UUID courseVersionId, EnrollmentStatus status,
            Instant grantedAt, boolean accessOpen) {
    }

    /** {@code retryAfter} is set while a rejection's cooldown runs. */
    public record AccessRequestView(UUID id, UUID courseId, String courseTitle, UUID learnerId, String learnerName, String learnerUsername,
            AccessRequestStatus status, String reason, Instant requestedAt, Instant decidedAt, Instant retryAfter, long revision) {
    }

    public record Decision(long expectedRevision, boolean approve, String reason) {
    }

    public record InvitationView(UUID id, UUID courseId, String email, InvitationStatus status, Instant createdAt, Instant expiresAt,
            Instant acceptedAt) {

        public static InvitationView of(CourseInvitation invitation) {
            return new InvitationView(invitation.getId(), invitation.getCourseId(), invitation.getRecipientEmail(), invitation.getStatus(), invitation.getCreatedAt(),
                    invitation.getExpiresAt(), invitation.getAcceptedAt());
        }
    }

    public record BulkInvitationResult(int invited, List<InvitationView> invitations) {
    }

    public record AcceptedInvitation(UUID enrollmentId, UUID courseId) {
    }
}
