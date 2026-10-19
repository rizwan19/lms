package com.saasresearcher.learning.identity.internal;

import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.institution.api.InstitutionSettings;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit demo mode (profile {@code demo}) for evaluation: creates demo-admin, demo-teacher and demo-student with a
 * published password on an empty installation. Refuses to start with production settings (secure cookies), so demo
 * credentials can never exist on a real deployment.
 */
@Component
@Profile("demo")
class DemoAccounts implements ApplicationRunner {

    static final String DEMO_PASSWORD = "demo-password-not-secret";
    private static final Logger log = LoggerFactory.getLogger(DemoAccounts.class);

    private final UserAccountRepository accounts;
    private final CredentialIssuer credentials;
    private final InstitutionSettings institution;
    private final Clock clock;
    private final boolean secureCookies;

    DemoAccounts(UserAccountRepository accounts, CredentialIssuer credentials, InstitutionSettings institution, Clock clock,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.institution = institution;
        this.clock = clock;
        this.secureCookies = secureCookies;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (secureCookies) {
            throw new IllegalStateException("Demo mode is for local evaluation only and cannot run with production settings "
                    + "(LEARNING_SECURE_COOKIES=true). Remove the 'demo' profile.");
        }
        if (accounts.existsWithRole(Role.ADMINISTRATOR)) {
            return;
        }
        institution.initialize("Demo School", ZoneId.of("Asia/Dhaka"));
        create("demo-admin", "Demo Administrator", "admin@demo.invalid", Role.ADMINISTRATOR);
        create("demo-teacher", "Demo Teacher", "teacher@demo.invalid", Role.TEACHER);
        create("demo-student", "Demo Student", null, Role.STUDENT);
        log.warn("Demo mode: created demo-admin, demo-teacher and demo-student with password '{}'", DEMO_PASSWORD);
    }

    private void create(String username, String displayName, String email, Role role) {
        UserAccount account = new UserAccount(username, email, displayName, Set.of(role), null, clock.instant());
        credentials.setPassword(account, DEMO_PASSWORD);
        accounts.save(account);
    }
}
