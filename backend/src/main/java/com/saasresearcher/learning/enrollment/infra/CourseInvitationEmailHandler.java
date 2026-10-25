package com.saasresearcher.learning.enrollment.infra;

import com.saasresearcher.learning.courses.api.CourseOwner;
import com.saasresearcher.learning.courses.api.CourseOwnershipService;
import com.saasresearcher.learning.enrollment.entity.CourseInvitation;
import com.saasresearcher.learning.enrollment.repository.CourseInvitationRepository;
import com.saasresearcher.learning.enrollment.service.InvitationTokens;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.notifications.api.EmailSender;
import com.saasresearcher.learning.notifications.api.JobHandler;
import com.saasresearcher.learning.notifications.api.JobRequest;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends an invitation link. Rotates the token under the invitation row lock first, so after a retry only the newest
 * email's link works and an accepted or revoked invitation is never mailed.
 */
@Component
public class CourseInvitationEmailHandler implements JobHandler {

    public static final String TYPE = "enrollment.invitation-email";

    private final CourseInvitationRepository invitations;
    private final CourseOwnershipService ownership;
    private final IdentityAccess identity;
    private final EmailSender email;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String publicOrigin;

    CourseInvitationEmailHandler(CourseInvitationRepository invitations, CourseOwnershipService ownership, IdentityAccess identity, EmailSender email,
                                 TransactionTemplate transactions, Clock clock, @Value("${learning.public-origin}") String publicOrigin) {
        this.invitations = invitations;
        this.ownership = ownership;
        this.identity = identity;
        this.email = email;
        this.transactions = transactions;
        this.clock = clock;
        this.publicOrigin = publicOrigin;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(JobRequest job) {
        transactions.execute(status -> rotate(job)).ifPresent(email::send);
    }

    private Optional<EmailMessage> rotate(JobRequest job) {
        Optional<CourseInvitation> stored = invitations.lockById(job.aggregateId()).filter(invitation -> invitation.isUsable(clock.instant()));
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        CourseInvitation invitation = stored.get();
        CourseOwner course = ownership.requireOwner(invitation.getCourseId());
        String inviter = identity.summaries(List.of(invitation.getInvitedBy())).get(invitation.getInvitedBy()).displayName();
        String token = InvitationTokens.newToken();
        invitation.rotate(InvitationTokens.sha256(token));
        return Optional.of(new EmailMessage(invitation.getRecipientEmail(), "Invitation to " + course.title(),
                inviter + " invited you to the course \"" + course.title() + "\".\n\nSign in or register with this email address, then accept"
                        + " within 7 days:\n\n" + publicOrigin + "/course-invitations/accept?token=" + token
                        + "\n\nIf you received several emails, use the latest one.\n"));
    }
}
