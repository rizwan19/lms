package com.saasresearcher.learning.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full application on a random port against a disposable PostgreSQL 17 container.
 * Every test starts from an empty installation (all tables truncated) with a reset clock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@Import({TestInfrastructure.class, TestClockConfiguration.class})
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    public static final String SETUP_TOKEN = "test-setup-token";
    public static final String PASSWORD = "correct horse battery";

    private static final List<String> PRESERVED_TABLES = List.of("flyway_schema_history", "institution_settings");

    @LocalServerPort
    protected int port;

    @LocalManagementPort
    protected int managementPort;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void resetInstallation() {
        clock.reset();
        List<String> tables = jdbc.sql("select tablename from pg_tables where schemaname = 'public'")
                .query(String.class).list().stream()
                .filter(table -> !PRESERVED_TABLES.contains(table))
                .toList();
        if (!tables.isEmpty()) {
            jdbc.sql("truncate table " + String.join(", ", tables) + " cascade").update();
        }
        jdbc.sql("update institution_settings set name = 'Learning Portal', timezone = 'UTC'").update();
    }

    /** A new client with its own cookie jar, i.e. a separate browser session. */
    protected ApiClient client() {
        return new ApiClient("http://localhost:" + port + "/api/v1");
    }

    protected ApiClient managementClient() {
        return new ApiClient("http://localhost:" + managementPort);
    }

    /** Completes installation setup and returns a signed-in administrator session. */
    protected ApiClient setupAdministrator() {
        ApiClient admin = client();
        admin.get("/auth/csrf");
        admin.post("/setup", Map.of("setupToken", SETUP_TOKEN, "institutionName", "Test School", "timezone", "Asia/Dhaka",
                "username", "admin", "displayName", "Administrator", "email", "admin@example.test", "password", PASSWORD))
                .expect(201);
        return login("admin", PASSWORD);
    }

    protected ApiClient login(String username, String password) {
        ApiClient session = client();
        session.get("/auth/csrf");
        session.post("/auth/login", Map.of("username", username, "password", password)).expect(200);
        session.get("/auth/csrf");
        return session;
    }

    /** Creates an account with a temporary password, then signs in and sets {@link #PASSWORD}. */
    protected TestUser createUser(ApiClient creator, String username, String email, String... roles) {
        ApiClient.ApiResponse created = creator.post("/users", Map.of(
                "username", username, "displayName", "User " + username, "email", email == null ? "" : email,
                "roles", List.of(roles), "delivery", "TEMPORARY_PASSWORD")).expect(201);
        String temporary = created.json().path("temporaryCredential").path("temporaryPassword").asString();
        ApiClient session = login(username, temporary);
        session.put("/auth/me/password", Map.of("currentPassword", temporary, "newPassword", PASSWORD)).expect(204);
        return new TestUser(UUID.fromString(created.json().path("account").path("id").asString()), username, session);
    }

    public record TestUser(UUID id, String username, ApiClient session) {
    }
}
