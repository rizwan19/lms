package com.saasresearcher.learning.identity.internal;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.identity.api.TemporaryCredential;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountRecoveryService {

    private final UserAccountRepository accounts;
    private final InvitationRepository invitations;
    private final PasswordResetTokenRepository resetTokens;
    private final AccountAuthorization authorization;
    private final CredentialIssuer credentials;
    private final SessionRevocation sessions;
    private final AuthRateLimiter rateLimiter;
    private final IdentityProperties properties;
    private final Clock clock;

    AccountRecoveryService(UserAccountRepository accounts, InvitationRepository invitations, PasswordResetTokenRepository resetTokens,
            AccountAuthorization authorization, CredentialIssuer credentials, SessionRevocation sessions,
            AuthRateLimiter rateLimiter, IdentityProperties properties, Clock clock) {
        this.accounts = accounts;
        this.invitations = invitations;
        this.resetTokens = resetTokens;
        this.authorization = authorization;
        this.credentials = credentials;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    /** Staff-assisted reset: one-use temporary password, all sessions revoked, change required at next sign-in. */
    @Transactional
    TemporaryCredential assistedReset(UUID userId, Actor actor) {
        UserAccount account = accounts.findById(userId).orElseThrow(ApiException::notFound);
        authorization.requireManageable(account, actor);
        if (account.id().equals(actor.userId())) {
            throw ApiException.invalid("userId", "Use change password for your own account");
        }
        TemporaryCredential credential = credentials.issueTemporaryPassword(account);
        sessions.revokeAfterCommit(account.id());
        return credential;
    }

    /** Always succeeds from the caller's view so account existence is not revealed. */
    @Transactional
    void requestPasswordReset(String identifier, String clientIp) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        String ipBucket = "reset-ip:" + clientIp;
        String accountBucket = "reset:" + normalized;
        if (rateLimiter.exceeded(ipBucket, properties.loginAttemptsPerIp())
                || rateLimiter.exceeded(accountBucket, properties.loginAttemptsPerAccount())) {
            return;
        }
        rateLimiter.record(ipBucket);
        rateLimiter.record(accountBucket);
        Optional<UserAccount> account = normalized.contains("@") ? accounts.findByEmail(normalized) : accounts.findByUsername(normalized);
        account.filter(UserAccount::isActive)
                .filter(found -> found.email() != null)
                .ifPresent(credentials::issuePasswordReset);
    }

    @Transactional
    void confirmPasswordReset(String token, String newPassword) {
        UserAccount account = redeem(resetTokens, token, newPassword);
        sessions.revokeAfterCommit(account.id());
    }

    @Transactional
    void acceptInvitation(String token, String password) {
        redeem(invitations, token, password);
    }

    @Transactional
    void changePassword(Actor actor, String currentPassword, String newPassword, String currentSessionId) {
        UserAccount account = accounts.findById(actor.userId()).orElseThrow(ApiException::notFound);
        if (!credentials.passwordMatches(account, currentPassword)) {
            throw ApiException.invalid("currentPassword", "Current password is incorrect");
        }
        AccountRules.password("newPassword", newPassword);
        if (newPassword.equals(currentPassword)) {
            throw ApiException.invalid("newPassword", "Choose a different password");
        }
        credentials.setPassword(account, newPassword);
        sessions.revokeAfterCommit(account.id(), currentSessionId);
    }

    private <T extends OneTimeToken> UserAccount redeem(OneTimeTokenRepository<T> repository, String token, String password) {
        AccountRules.password("password", password);
        T stored = repository.findByTokenHash(SecureTokens.sha256(token == null ? "" : token))
                .filter(candidate -> candidate.isUsable(clock.instant()))
                .orElseThrow(() -> ApiException.badRequest(IdentityErrors.INVALID_TOKEN, "This link is invalid or has expired"));
        UserAccount account = accounts.findById(stored.userId())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> ApiException.badRequest(IdentityErrors.INVALID_TOKEN, "This link is invalid or has expired"));
        stored.markUsed(clock.instant());
        credentials.setPassword(account, password);
        return account;
    }
}
