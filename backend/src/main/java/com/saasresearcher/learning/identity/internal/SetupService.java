package com.saasresearcher.learning.identity.internal;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.institution.api.InstitutionSettings;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One-use installation setup guarded by an operator-supplied token; disabled once an administrator exists. */
@Service
class SetupService {

    private final UserAccountRepository accounts;
    private final CredentialIssuer credentials;
    private final InstitutionSettings institution;
    private final AuditLog audit;
    private final IdentityProperties properties;
    private final Clock clock;

    SetupService(UserAccountRepository accounts, CredentialIssuer credentials, InstitutionSettings institution, AuditLog audit,
            IdentityProperties properties, Clock clock) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.institution = institution;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    boolean isSetupRequired() {
        return !accounts.existsWithRole(Role.ADMINISTRATOR);
    }

    @Transactional
    UserAccount setup(SetupCommand command) {
        institution.lock();
        if (!isSetupRequired()) {
            throw ApiException.conflict(IdentityErrors.SETUP_COMPLETED, "Setup has already been completed");
        }
        String expected = properties.setupToken();
        if (expected == null || expected.isBlank() || command.setupToken() == null
                || !SecureTokens.constantTimeEquals(expected, command.setupToken())) {
            throw new ApiException(HttpStatus.FORBIDDEN, IdentityErrors.SETUP_TOKEN_INVALID, "The setup token is not valid");
        }
        String email = AccountRules.email(command.email());
        if (email == null) {
            throw ApiException.invalid("email", "Administrators need an email address");
        }
        AccountRules.password("password", command.password());
        UserAccount admin = new UserAccount(AccountRules.username(command.username()), email,
                AccountRules.displayName(command.displayName()), Set.of(Role.ADMINISTRATOR), null, clock.instant());
        credentials.setPassword(admin, command.password());
        institution.initialize(command.institutionName().trim(), command.timezone());
        accounts.save(admin);
        audit.record(AuditEntry.success(admin.id(), "setup.completed", "user", admin.id()));
        return admin;
    }

    record SetupCommand(String setupToken, String institutionName, ZoneId timezone, String username,
            String displayName, String email, String password) {
    }
}
