package com.saasresearcher.learning.support;

import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.notifications.infra.JobRunner;
import com.saasresearcher.learning.support.TestJobsConfiguration.CapturingEmailSender;
import com.saasresearcher.learning.support.TestJobsConfiguration.RecordingJobHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

/**
 * Boots the full application on a random port against a disposable PostgreSQL 17 container.
 * Every test starts from an empty installation (all tables truncated, workspace not set up) with a reset clock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@Import({TestInfrastructure.class, TestClockConfiguration.class, TestJobsConfiguration.class, RecordingProgressObserver.class})
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    public static final String PASSWORD = "correct horse battery";

    private static final List<String> PRESERVED_TABLES = List.of("flyway_schema_history", "workspace", "system_state");
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @LocalServerPort
    protected int port;

    @LocalManagementPort
    protected int managementPort;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected CapturingEmailSender email;

    @Autowired
    protected RecordingJobHandler recordingJobs;

    @Autowired
    protected RecordingProgressObserver progressObserver;

    @Autowired
    protected JobRunner jobRunner;

    @BeforeEach
    void resetInstallation() {
        clock.reset();
        email.reset();
        recordingJobs.reset();
        progressObserver.reset();
        List<String> tables = jdbc.sql("select tablename from pg_tables where schemaname = 'public'")
                .query(String.class).list().stream()
                .filter(table -> !PRESERVED_TABLES.contains(table))
                .toList();
        if (!tables.isEmpty()) {
            jdbc.sql("truncate table " + String.join(", ", tables) + " cascade").update();
        }
        // The workspace references users and media, so the cascade empties it; restore the singleton as never set up.
        jdbc.sql("""
                insert into workspace (id, name, timezone, state, created_at, updated_at) values (1, 'Learning Portal', 'UTC', 0, now(), now())
                on conflict (id) do update set name = excluded.name, timezone = excluded.timezone, state = 0, created_by = null,
                    logo_asset_id = null, public_verification = false, certificate_show_name = false, version = 0
                """).update();
        jdbc.sql("update system_state set maintenance = false, reason = null").update();
    }

    /** A new client with its own cookie jar, i.e. a separate browser session. */
    protected ApiClient client() {
        ApiClient client = new ApiClient("http://localhost:" + port + "/api/v1");
        client.get("/auth/csrf");
        return client;
    }

    protected ApiClient managementClient() {
        return new ApiClient("http://localhost:" + managementPort);
    }

    protected void runJobs() {
        jobRunner.drain();
    }

    /** Runs pending jobs and returns the token from the newest email to {@code address}. */
    protected String tokenFor(String address) {
        runJobs();
        List<EmailMessage> sent = email.sent().stream().filter(message -> message.to().equals(address)).toList();
        if (sent.isEmpty()) {
            throw new AssertionError("No email was sent to " + address);
        }
        Matcher matcher = TOKEN.matcher(sent.getLast().body());
        if (!matcher.find()) {
            throw new AssertionError("No token in the email to " + address);
        }
        return matcher.group(1);
    }

    /** Completes workspace setup and owner verification; returns the signed-in, approved owner. */
    protected TestUser setupOwner() {
        ApiClient anonymous = client();
        anonymous.post("/setup", Map.of("workspaceName", "Test School", "timezone", "Asia/Dhaka", "username", "owner",
                "displayName", "Owner", "email", "owner@example.test", "password", PASSWORD)).expect(202);
        anonymous.post("/auth/email/verify", Map.of("token", tokenFor("owner@example.test"))).expect(204);
        ApiClient session = login("owner", PASSWORD);
        return new TestUser(id(session), "owner", "owner@example.test", session);
    }

    /** A verified student. */
    protected TestUser registerStudent(String username) {
        return register(username, "STUDENT");
    }

    /** A verified teacher whose application is still pending. */
    protected TestUser registerTeacher(String username) {
        return register(username, "TEACHER");
    }

    /** A verified teacher approved by {@code reviewer}. */
    protected TestUser approvedTeacher(String username, TestUser reviewer) {
        TestUser teacher = registerTeacher(username);
        approve(teacher, reviewer);
        return teacher;
    }

    protected void approve(TestUser applicant, TestUser reviewer) {
        JsonNode application = reviewer.session().get("/workspace/teacher-applications").expect(200).json().path("items").valueStream()
                .filter(item -> item.path("applicantId").asString().equals(applicant.id().toString()))
                .findFirst().orElseThrow(() -> new AssertionError("No pending application for " + applicant.username()));
        reviewer.session().post("/workspace/teacher-applications/" + application.path("id").asString() + "/decision",
                Map.of("expectedRevision", application.path("revision").asLong(), "approve", true)).expect(200);
    }

    protected TestUser register(String username, String role) {
        String address = username + "@example.test";
        ApiClient anonymous = client();
        anonymous.post("/auth/register", Map.of("username", username, "displayName", "User " + username, "email", address,
                "password", PASSWORD, "role", role)).expect(201);
        anonymous.post("/auth/email/verify", Map.of("token", tokenFor(address))).expect(204);
        ApiClient session = login(username, PASSWORD);
        return new TestUser(id(session), username, address, session);
    }

    protected ApiClient login(String username, String password) {
        ApiClient session = client();
        session.post("/auth/login", Map.of("username", username, "password", password)).expect(200);
        session.get("/auth/csrf");
        return session;
    }

    private static UUID id(ApiClient session) {
        return UUID.fromString(session.get("/auth/me").expect(200).json().path("id").asString());
    }

    public record TestUser(UUID id, String username, String email, ApiClient session) {
    }
}
