package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.common.Timezones;
import com.saasresearcher.learning.identity.api.TokenPurpose;
import com.saasresearcher.learning.identity.dto.AccountViews.RegistrationReceipt;
import com.saasresearcher.learning.identity.dto.SetupRequest;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.identity.service.RegistrationService.RegisterCommand;
import com.saasresearcher.learning.notifications.api.EmailSender;
import com.saasresearcher.learning.workspace.api.WorkspaceService;
import com.saasresearcher.learning.workspace.api.WorkspaceState;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetupService {

    private final UserAccountRepository userAccountRepository;
    private final RegistrationService registrationService;
    private final CredentialIssuer credentialIssuer;
    private final WorkspaceService workspaceService;
    private final EmailSender emailSender;
    private final AuditService auditService;


    public boolean isSetupRequired() {
        WorkspaceState state = getState();
        return state.equals(WorkspaceState.REQUIRED);
    }

    public WorkspaceState getState() {
        return workspaceService.getWorkspaceData().state();
    }

    @Transactional
    public RegistrationReceipt setup(SetupRequest request) {
        ZoneId timezone = Timezones.parse("timezone", request.timezone());
        workspaceService.lockWorkspace();
        if (workspaceService.getWorkspaceData().state() != WorkspaceState.REQUIRED) {
            throw ApiException.conflict(IdentityErrors.SETUP_COMPLETED, "Setup has already been started");
        }
        if (!emailSender.isConfigured()) {
            throw ApiException.conflict(IdentityErrors.EMAIL_NOT_CONFIGURED, "Configure SMTP before setup: the owner must verify their email");
        }
        String name = request.workspaceName() == null ? "" : request.workspaceName().trim();
        if (name.isEmpty() || name.length() > 200) {
            throw ApiException.invalid("workspaceName", "Name must be 1-200 characters");
        }
        UserAccount owner = registrationService.newAccount(new RegisterCommand(request.username(), request.displayName(), request.email(),
                request.password(), Role.TEACHER));
        userAccountRepository.save(owner);
        workspaceService.beginSetup(name, timezone, owner.getId());
        credentialIssuer.issue(owner, TokenPurpose.OWNER_VERIFY_EMAIL);
        auditService.record(AuditEntry.success(owner.getId(), "setup.started", "workspace", null));
        return new RegistrationReceipt(owner.getId(), RegistrationReceipt.VERIFY_EMAIL);
    }
}
