package com.saasresearcher.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountSecurityIT extends PostgresIntegrationTest {

    @BeforeEach
    void workspace() {
        setupOwner();
    }

    @Test
    void usernamesAreCaseInsensitiveForSignIn() {
        registerStudent("alice.rahman");
        assertThat(login("ALICE.RAHMAN", PASSWORD).get("/auth/me").json().path("username").asString()).isEqualTo("alice.rahman");
        assertThat(login("Alice.Rahman@Example.test", PASSWORD).get("/auth/me").status()).isEqualTo(200);
    }

    @Test
    void passwordChangeRevokesOtherSessionsButKeepsCurrent() {
        TestUser student = registerStudent("two.sessions");
        ApiClient other = login("two.sessions", PASSWORD);

        assertThat(student.session().put("/auth/me/password", Map.of("currentPassword", "wrong password!!", "newPassword", "x".repeat(12)))
                .json().path("fieldErrors").has("currentPassword")).isTrue();
        student.session().put("/auth/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "another long passphrase")).expect(204);

        assertThat(student.session().get("/auth/me").status()).isEqualTo(200);
        assertThat(other.get("/auth/me").status()).isEqualTo(401);
    }

    @Test
    void loginIsRateLimitedPerAccountAndAddress() {
        registerStudent("guessed");
        ApiClient attacker = client();
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(attacker.post("/auth/login", Map.of("username", "guessed", "password", "wrong password!")).status()).isEqualTo(401);
        }
        ApiResponse limited = attacker.post("/auth/login", Map.of("username", "guessed", "password", PASSWORD));
        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.code()).isEqualTo("RATE_LIMITED");

        clock.advance(Duration.ofMinutes(16));
        assertThat(attacker.post("/auth/login", Map.of("username", "guessed", "password", PASSWORD)).status()).isEqualTo(200);
    }

    @Test
    void mutationsRequireCsrfTokenAndSessionCookieIsHardened() {
        ApiClient browser = new ApiClient("http://localhost:" + port + "/api/v1");
        ApiResponse withoutToken = browser.post("/auth/login", Map.of("username", "owner", "password", PASSWORD));
        assertThat(withoutToken.status()).isEqualTo(403);
        assertThat(withoutToken.code()).isEqualTo("CSRF_REJECTED");

        browser.get("/auth/csrf");
        String tokenBeforeLogin = browser.csrfToken().orElseThrow();
        ApiResponse login = browser.post("/auth/login", Map.of("username", "owner", "password", PASSWORD)).expect(200);
        String sessionCookie = login.headers().getOrDefault("set-cookie", List.of()).stream()
                .filter(cookie -> cookie.startsWith("LEARNING_SESSION=")).findFirst().orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax");
        browser.get("/auth/csrf");
        assertThat(browser.csrfToken().orElseThrow()).isNotEqualTo(tokenBeforeLogin);

        browser.clearCookie("XSRF-TOKEN");
        assertThat(browser.post("/auth/logout", null).status()).isEqualTo(403);
        browser.get("/auth/csrf");
        assertThat(browser.post("/auth/logout", null).status()).isEqualTo(204);
        assertThat(browser.get("/auth/me").status()).isEqualTo(401);
    }

    @Test
    void registrationIsRateLimitedPerAddress() {
        for (int i = 0; i < 20; i++) {
            client().post("/auth/register", Map.of("username", "bulk" + i, "displayName", "Bulk", "email", "bulk" + i + "@example.test",
                    "password", PASSWORD, "role", "STUDENT")).expect(201);
        }
        assertThat(client().post("/auth/register", Map.of("username", "bulk20", "displayName", "Bulk", "email", "bulk20@example.test",
                "password", PASSWORD, "role", "STUDENT")).status()).isEqualTo(429);
    }
}
