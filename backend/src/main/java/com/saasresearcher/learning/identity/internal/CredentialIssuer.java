package com.saasresearcher.learning.identity.internal;

import com.saasresearcher.learning.identity.api.TemporaryCredential;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Issues passwords and one-time tokens; callers own the transaction. */
@Component
class CredentialIssuer {

    private final PasswordEncoder passwordEncoder;
    private final InvitationRepository invitations;
    private final PasswordResetTokenRepository resetTokens;
    private final IdentityProperties properties;
    private final Clock clock;

    CredentialIssuer(PasswordEncoder passwordEncoder, InvitationRepository invitations,
            PasswordResetTokenRepository resetTokens, IdentityProperties properties, Clock clock) {
        this.passwordEncoder = passwordEncoder;
        this.invitations = invitations;
        this.resetTokens = resetTokens;
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

    void setPassword(UserAccount account, String password) {
        account.setPassword(passwordEncoder.encode(password), clock.instant());
    }

    boolean passwordMatches(UserAccount account, String password) {
        return account.passwordHash() != null && passwordEncoder.matches(password, account.passwordHash());
    }

    /** Creates an invitation and returns the raw token, which exists only for delivery. */
    IssuedToken issueInvitation(UserAccount account) {
        Instant now = clock.instant();
        String token = SecureTokens.newToken();
        Invitation invitation = invitations.save(new Invitation(account.id(), SecureTokens.sha256(token), now.plus(properties.invitationTtl()), now));
        return new IssuedToken(token, invitation.expiresAt());
    }

    IssuedToken issuePasswordReset(UserAccount account) {
        Instant now = clock.instant();
        String token = SecureTokens.newToken();
        PasswordResetToken reset = resetTokens.save(new PasswordResetToken(account.id(), SecureTokens.sha256(token), now.plus(properties.passwordResetTtl()), now));
        return new IssuedToken(token, reset.expiresAt());
    }

    record IssuedToken(String token, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedToken[expiresAt=" + expiresAt + "]";
        }
    }
}
