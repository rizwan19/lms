package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.identity.api.TemporaryCredential;
import com.saasresearcher.learning.identity.config.IdentityProperties;
import com.saasresearcher.learning.identity.entity.Invitation;
import com.saasresearcher.learning.identity.entity.PasswordResetToken;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.infra.InvitationEmailHandler;
import com.saasresearcher.learning.identity.infra.PasswordResetEmailHandler;
import com.saasresearcher.learning.identity.repository.InvitationRepository;
import com.saasresearcher.learning.identity.repository.PasswordResetTokenRepository;
import com.saasresearcher.learning.notifications.api.DurableJobs;
import com.saasresearcher.learning.notifications.api.JobRequest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Issues passwords and one-time tokens; callers own the transaction. */
@Component
public class CredentialIssuer {

    private final PasswordEncoder passwordEncoder;
    private final InvitationRepository invitations;
    private final PasswordResetTokenRepository resetTokens;
    private final DurableJobs jobs;
    private final IdentityProperties properties;
    private final Clock clock;

    CredentialIssuer(PasswordEncoder passwordEncoder, InvitationRepository invitations, PasswordResetTokenRepository resetTokens,
            DurableJobs jobs, IdentityProperties properties, Clock clock) {
        this.passwordEncoder = passwordEncoder;
        this.invitations = invitations;
        this.resetTokens = resetTokens;
        this.jobs = jobs;
        this.properties = properties;
        this.clock = clock;
    }

    TemporaryCredential issueTemporaryPassword(UserAccount account) {
        Instant now = clock.instant();
        String password = SecureTokens.newTemporaryPassword();
        Instant expiresAt = now.plus(properties.temporaryPasswordTtl());
        account.setTemporaryPassword(passwordEncoder.encode(password), expiresAt, now);
        return new TemporaryCredential(account.username(), password, expiresAt);
    }

    public void setPassword(UserAccount account, String password) {
        account.setPassword(passwordEncoder.encode(password), clock.instant());
    }

    boolean passwordMatches(UserAccount account, String password) {
        return account.passwordHash() != null && passwordEncoder.matches(password, account.passwordHash());
    }

    /**
     * Creates an invitation and queues its email. The stored hash belongs to a token nobody sees; the email
     * job rotates it and sends the fresh token, so no usable token is ever persisted in plain text.
     */
    Instant issueInvitation(UserAccount account) {
        Instant now = clock.instant();
        Invitation invitation = invitations.save(new Invitation(account.id(), unusedTokenHash(), now.plus(properties.invitationTtl()), now));
        jobs.enqueue(JobRequest.now(InvitationEmailHandler.TYPE, invitation.id(), "invitation-email:" + invitation.id(), now));
        return invitation.expiresAt();
    }

    void issuePasswordReset(UserAccount account) {
        Instant now = clock.instant();
        PasswordResetToken reset = resetTokens.save(new PasswordResetToken(account.id(), unusedTokenHash(), now.plus(properties.passwordResetTtl()), now));
        jobs.enqueue(JobRequest.now(PasswordResetEmailHandler.TYPE, reset.id(), "password-reset-email:" + reset.id(), now));
    }

    private static String unusedTokenHash() {
        return SecureTokens.sha256(SecureTokens.newToken());
    }
}
