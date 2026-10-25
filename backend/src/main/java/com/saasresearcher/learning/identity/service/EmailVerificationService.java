package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.common.TeacherStatus;
import com.saasresearcher.learning.identity.api.TokenPurpose;
import com.saasresearcher.learning.identity.config.IdentityProperties;
import com.saasresearcher.learning.identity.entity.IdentityToken;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.IdentityTokenRepository;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.workspace.api.WorkspaceData;
import com.saasresearcher.learning.workspace.api.WorkspaceService;
import com.saasresearcher.learning.workspace.api.WorkspaceState;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email verification. The token is consumed while the user and token rows are locked, so a link works once even when
 * clicked twice concurrently. Verifying the workspace owner approves them and activates the workspace; verifying any
 * other teacher opens their application for peer review exactly once.
 */
@Service
public class EmailVerificationService {

    private static final List<TokenPurpose> VERIFICATION = List.of(TokenPurpose.VERIFY_EMAIL, TokenPurpose.OWNER_VERIFY_EMAIL);

    private final IdentityTokenRepository tokens;
    private final UserAccountRepository accounts;
    private final CredentialIssuer credentials;
    private final TeacherApplicationService applications;
    private final WorkspaceService workspace;
    private final AuthRateLimiter rateLimiter;
    private final AuditService audit;
    private final IdentityProperties properties;
    private final Clock clock;
    private final RowLocks locks;

    EmailVerificationService(IdentityTokenRepository tokens, UserAccountRepository accounts, CredentialIssuer credentials,
            TeacherApplicationService applications, WorkspaceService workspace, AuthRateLimiter rateLimiter, AuditService audit,
            IdentityProperties properties, RowLocks locks, Clock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.credentials = credentials;
        this.applications = applications;
        this.workspace = workspace;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.locks = locks;
    }

    @Transactional
    public void verify(String rawToken) {
        IdentityToken found = tokens.findByTokenHash(SecureTokens.sha256(rawToken == null ? "" : rawToken))
                .filter(token -> VERIFICATION.contains(token.getPurpose()))
                .orElseThrow(EmailVerificationService::invalid);
        boolean owner = found.getPurpose() == TokenPurpose.OWNER_VERIFY_EMAIL;
        if (owner) {
            workspace.lockWorkspace();
        }
        UserAccount account = accounts.lockById(found.getUserId()).filter(UserAccount::isActive).orElseThrow(EmailVerificationService::invalid);
        IdentityToken token = locks.forUpdate(found);
        Instant now = clock.instant();
        if (!token.isUsable(now)) {
            throw invalid();
        }
        token.consume(now);
        credentials.revokeOpen(account, VERIFICATION);
        account.verifyEmail(now);
        if (owner) {
            account.setTeacherStatus(TeacherStatus.APPROVED, now);
            workspace.activate();
            audit.record(AuditEntry.success(account.getId(), "setup.owner-verified", "workspace", null));
        } else if (account.getRole() == Role.TEACHER) {
            applications.open(account);
        }
        audit.record(AuditEntry.success(account.getId(), "email.verified", "user", account.getId()));
    }

    /** Invalidates the previous link and queues a new one; the owner keeps their owner-verification purpose. */
    @Transactional
    public void resend(Actor actor) {
        rateLimiter.acquire("verify-resend:" + actor.userId(), properties.verificationResendsPerAccount());
        UserAccount account = accounts.lockById(actor.userId()).orElseThrow(ApiException::notFound);
        if (account.isEmailVerified()) {
            throw ApiException.conflict(IdentityErrors.ALREADY_VERIFIED, "Your email address is already verified");
        }
        WorkspaceData current = workspace.getWorkspaceData();
        boolean owner = current.state() == WorkspaceState.OWNER_VERIFICATION_PENDING && account.getId().equals(current.ownerId());
        credentials.revokeOpen(account, VERIFICATION);
        credentials.issue(account, owner ? TokenPurpose.OWNER_VERIFY_EMAIL : TokenPurpose.VERIFY_EMAIL);
    }

    private static ApiException invalid() {
        return ApiException.badRequest(IdentityErrors.INVALID_TOKEN, "This link is invalid or has expired; request a new one");
    }
}
