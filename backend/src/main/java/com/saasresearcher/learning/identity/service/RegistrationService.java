package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.identity.api.TokenPurpose;
import com.saasresearcher.learning.identity.config.IdentityProperties;
import com.saasresearcher.learning.identity.dto.AccountViews.RegistrationReceipt;
import com.saasresearcher.learning.identity.dto.RegisterRequest;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.notifications.api.EmailSender;
import com.saasresearcher.learning.workspace.api.WorkspaceService;
import com.saasresearcher.learning.workspace.api.WorkspaceState;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public signup for either role. The account starts unverified and receives a verification email; no session or token
 * is returned. Teachers start PENDING and enter the review queue only after verification.
 */
@Service
public class RegistrationService {

    private final UserAccountRepository accounts;
    private final CredentialIssuer credentials;
    private final WorkspaceService workspace;
    private final EmailSender email;
    private final AuthRateLimiter rateLimiter;
    private final AuditService audit;
    private final IdentityProperties properties;
    private final Clock clock;

    RegistrationService(UserAccountRepository accounts, CredentialIssuer credentials, WorkspaceService workspace, EmailSender email,
            AuthRateLimiter rateLimiter, AuditService audit, IdentityProperties properties, Clock clock) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.workspace = workspace;
        this.email = email;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public RegistrationReceipt register(RegisterRequest request, String clientIp) {
        if (workspace.getWorkspaceData().state() != WorkspaceState.ACTIVE) {
            throw ApiException.conflict(IdentityErrors.REGISTRATION_CLOSED, "Registration opens once the workspace setup is complete");
        }
        if (!email.isConfigured()) {
            throw ApiException.conflict(IdentityErrors.EMAIL_NOT_CONFIGURED, "Email delivery is not configured; registration is unavailable");
        }
        rateLimiter.acquire("register-ip:" + clientIp, properties.registrationsPerIp());
        if (request.role() == null) {
            throw ApiException.invalid("role", "Choose TEACHER or STUDENT");
        }
        UserAccount account = newAccount(request);
        accounts.save(account);
        credentials.issue(account, TokenPurpose.VERIFY_EMAIL);
        audit.record(AuditEntry.success(account.getId(), "account.registered", "user", account.getId(), Map.of("role", account.getRole())));
        return new RegistrationReceipt(account.getId(), RegistrationReceipt.VERIFY_EMAIL);
    }

    /** Validates and creates an unverified account; uniqueness races are caught by the database constraints. */
    UserAccount newAccount(RegisterRequest request) {
        String username = AccountRules.username(request.username());
        String normalizedEmail = AccountRules.email(request.email());
        String displayName = AccountRules.displayName(request.displayName());
        AccountRules.password("password", request.password());
        if (accounts.existsByUsername(username)) {
            throw ApiException.conflict(IdentityErrors.USERNAME_TAKEN, "That username is already in use");
        }
        if (accounts.existsByEmail(normalizedEmail)) {
            throw ApiException.conflict(IdentityErrors.EMAIL_TAKEN, "That email address is already in use");
        }
        return new UserAccount(username, normalizedEmail, displayName, request.role(), credentials.hash(request.password()), clock.instant());
    }
}
