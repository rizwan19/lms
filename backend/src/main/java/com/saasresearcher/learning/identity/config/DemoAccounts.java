package com.saasresearcher.learning.identity.config;

import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.common.TeacherStatus;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.identity.service.CredentialIssuer;
import com.saasresearcher.learning.workspace.api.WorkspaceService;
import com.saasresearcher.learning.workspace.api.WorkspaceState;
import java.time.Clock;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit demo mode (profile {@code demo}) for evaluation: on an installation that was never set up, creates an
 * active workspace owned by a verified, approved demo-teacher plus a verified demo-student, both with a published
 * password. Refuses to start with production settings (secure cookies), so demo accounts and their verification
 * shortcut can never exist on a real deployment.
 */
@Component
@Profile("demo")
public class DemoAccounts implements ApplicationRunner {

    static final String DEMO_PASSWORD = "demo-password-not-secret";
    private static final Logger log = LoggerFactory.getLogger(DemoAccounts.class);

    private final UserAccountRepository accounts;
    private final CredentialIssuer credentials;
    private final WorkspaceService workspace;
    private final Clock clock;
    private final boolean secureCookies;

    DemoAccounts(UserAccountRepository accounts, CredentialIssuer credentials, WorkspaceService workspace, Clock clock,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.workspace = workspace;
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
        workspace.lockWorkspace();
        if (workspace.getWorkspaceData().state() != WorkspaceState.REQUIRED) {
            return;
        }
        UserAccount teacher = create("demo-teacher", "Demo Teacher", "teacher@demo.invalid", Role.TEACHER);
        teacher.setTeacherStatus(TeacherStatus.APPROVED, clock.instant());
        create("demo-student", "Demo Student", "student@demo.invalid", Role.STUDENT);
        accounts.flush();
        workspace.beginSetup("Demo School", ZoneId.of("Asia/Dhaka"), teacher.getId());
        workspace.activate();
        log.warn("Demo mode: created demo-teacher and demo-student with password '{}'", DEMO_PASSWORD);
    }

    private UserAccount create(String username, String displayName, String email, Role role) {
        UserAccount account = new UserAccount(username, email, displayName, role, credentials.hash(DEMO_PASSWORD), clock.instant());
        account.verifyEmail(clock.instant());
        return accounts.save(account);
    }
}
