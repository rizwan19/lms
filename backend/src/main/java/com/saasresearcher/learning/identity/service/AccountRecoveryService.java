package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.identity.api.TokenPurpose;
import com.saasresearcher.learning.identity.config.IdentityProperties;
import com.saasresearcher.learning.identity.entity.IdentityToken;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.IdentityTokenRepository;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.notifications.api.EmailSender;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Password recovery through the verified email address, and password changes by the signed-in owner. */
@Service
public class AccountRecoveryService {

    private final UserAccountRepository accounts;
    private final IdentityTokenRepository tokens;
    private final CredentialIssuer credentials;
    private final SessionRevocation sessions;
    private final AuthRateLimiter rateLimiter;
    private final EmailSender email;
    private final AuditService audit;
    private final IdentityProperties properties;
    private final Clock clock;
    private final RowLocks locks;

    AccountRecoveryService(UserAccountRepository accounts, IdentityTokenRepository tokens, CredentialIssuer credentials,
            SessionRevocation sessions, AuthRateLimiter rateLimiter, EmailSender email, AuditService audit, IdentityProperties properties,
            RowLocks locks, Clock clock) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.credentials = credentials;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.email = email;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.locks = locks;
    }

    /** Always succeeds from the caller's view so account existence is not revealed. */
    @Transactional
    public void requestPasswordReset(String identifier, String clientIp) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        String ipBucket = "reset-ip:" + clientIp;
        String accountBucket = "reset:" + normalized;
        if (!email.isConfigured() || rateLimiter.exceeded(ipBucket, properties.loginAttemptsPerIp())
                || rateLimiter.exceeded(accountBucket, properties.loginAttemptsPerAccount())) {
            return;
        }
        rateLimiter.record(ipBucket);
        rateLimiter.record(accountBucket);
        Optional<UserAccount> account = normalized.contains("@") ? accounts.findByEmail(normalized) : accounts.findByUsername(normalized);
        account.filter(UserAccount::isActive)
                .filter(UserAccount::isEmailVerified)
                .ifPresent(found -> credentials.issue(found, TokenPurpose.RESET_PASSWORD));
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        AccountRules.password("password", newPassword);
        IdentityToken found = tokens.findByTokenHash(SecureTokens.sha256(rawToken == null ? "" : rawToken))
                .filter(token -> token.getPurpose() == TokenPurpose.RESET_PASSWORD)
                .orElseThrow(AccountRecoveryService::invalid);
        UserAccount account = accounts.lockById(found.getUserId()).filter(UserAccount::isActive).orElseThrow(AccountRecoveryService::invalid);
        IdentityToken token = locks.forUpdate(found);
        Instant now = clock.instant();
        if (!token.isUsable(now)) {
            throw invalid();
        }
        token.consume(now);
        credentials.revokeOpen(account, List.of(TokenPurpose.RESET_PASSWORD));
        credentials.setPassword(account, newPassword);
        sessions.revokeAfterCommit(account.getId());
        audit.record(AuditEntry.success(account.getId(), "password.reset", "user", account.getId()));
    }

    @Transactional
    public void changePassword(Actor actor, String currentPassword, String newPassword, String currentSessionId) {
        UserAccount account = accounts.lockById(actor.userId()).orElseThrow(ApiException::notFound);
        if (currentPassword == null || !credentials.passwordMatches(account, currentPassword)) {
            throw ApiException.invalid("currentPassword", "Current password is incorrect");
        }
        AccountRules.password("newPassword", newPassword);
        if (newPassword.equals(currentPassword)) {
            throw ApiException.invalid("newPassword", "Choose a different password");
        }
        credentials.setPassword(account, newPassword);
        sessions.revokeAfterCommit(account.getId(), currentSessionId);
        audit.record(AuditEntry.success(account.getId(), "password.changed", "user", account.getId()));
    }

    private static ApiException invalid() {
        return ApiException.badRequest(IdentityErrors.INVALID_TOKEN, "This link is invalid or has expired");
    }
}
