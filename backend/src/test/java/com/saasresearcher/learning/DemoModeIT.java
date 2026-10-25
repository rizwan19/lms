package com.saasresearcher.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Demo credentials exist only in explicit demo mode and never with production settings. */
class DemoModeIT extends PostgresIntegrationTest {

    @Autowired
    private PostgreSQLContainer database;

    @Test
    void demoModeSeedsEvaluationAccountsOnAnEmptyInstallation() {
        try (ConfigurableApplicationContext demo = start("--server.servlet.session.cookie.secure=false")) {
            ApiClient session = new ApiClient("http://localhost:" + demo.getEnvironment().getProperty("local.server.port") + "/api/v1");
            session.get("/auth/csrf");
            assertThat(session.post("/auth/login", Map.of("username", "demo-teacher", "password", "demo-password-not-secret")).status()).isEqualTo(200);
            session.get("/auth/csrf");
            assertThat(session.get("/auth/me").json().path("capabilities").path("canPublishCourses").asBoolean()).isTrue();
            assertThat(session.get("/setup").json().asString()).isEqualTo("ACTIVE");
        }
    }

    @Test
    void demoModeRefusesProductionSettings() {
        assertThatThrownBy(() -> start("--server.servlet.session.cookie.secure=true").close())
                .hasMessage("Demo mode is for local evaluation only and cannot run with production settings "
                        + "(LEARNING_SECURE_COOKIES=true). Remove the 'demo' profile.");
    }

    @Test
    void ordinaryInstallationsHaveNoDemoAccounts() {
        assertThat(jdbc.sql("select count(*) from users where username like 'demo-%'").query(Long.class).single()).isZero();
    }

    private ConfigurableApplicationContext start(String cookieSetting) {
        return new SpringApplicationBuilder(LearningPortalApplication.class).profiles("test", "demo").run(
                "--spring.datasource.url=" + database.getJdbcUrl(), "--spring.datasource.username=" + database.getUsername(),
                "--spring.datasource.password=" + database.getPassword(), "--server.port=0", "--management.server.port=0", cookieSetting);
    }
}
