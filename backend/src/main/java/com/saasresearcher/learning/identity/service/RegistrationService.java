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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final CredentialIssuer credentialIssuer;
    private final WorkspaceService workspaceService;
    private final EmailSender emailSender;
    private final AuthRateLimiter rateLimiter;
    private final AuditService auditService;
    private final IdentityProperties identityProperties;
    private final Clock clock;

    @Transactional
    public RegistrationReceipt register(RegisterRequest request, String clientIp) {
        if (workspaceService.getWorkspaceData().state() != WorkspaceState.ACTIVE) {
            throw ApiException.conflict(IdentityErrors.REGISTRATION_CLOSED, "Registration opens once the workspace setup is complete");
        }
        if (!emailSender.isConfigured()) {
            throw ApiException.conflict(IdentityErrors.EMAIL_NOT_CONFIGURED, "Email delivery is not configured; registration is unavailable");
        }
        rateLimiter.acquire("register-ip:" + clientIp, identityProperties.registrationsPerIp());
        if (request.role() == null) {
            throw ApiException.invalid("role", "Choose TEACHER or STUDENT");
        }
        UserAccount account = newAccount(request);
        userAccountRepository.save(account);
        credentialIssuer.issue(account, TokenPurpose.VERIFY_EMAIL);
        auditService.record(AuditEntry.success(account.getId(), "account.registered", "user", account.getId(), Map.of("role", account.getRole())));
        return new RegistrationReceipt(account.getId(), RegistrationReceipt.VERIFY_EMAIL);
    }

    UserAccount newAccount(RegisterRequest request) {
        String username = AccountRules.username(request.username());
        String normalizedEmail = AccountRules.email(request.email());
        String displayName = AccountRules.displayName(request.displayName());
        AccountRules.password("password", request.password());
        if (userAccountRepository.existsByUsername(username)) {
            throw ApiException.conflict(IdentityErrors.USERNAME_TAKEN, "That username is already in use");
        }
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw ApiException.conflict(IdentityErrors.EMAIL_TAKEN, "That email address is already in use");
        }
        return new UserAccount(username, normalizedEmail, displayName, request.role(), credentialIssuer.hash(request.password()), clock.instant());
    }
}
