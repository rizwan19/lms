package com.saasresearcher.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountSecurityIT extends PostgresIntegrationTest {

    @Test
    void setupIsOneUse() {
        ApiClient anonymous = client();
        anonymous.get("/auth/csrf");
        assertThat(anonymous.get("/setup").json().path("required").asBoolean()).isTrue();

        Map<String, String> request = Map.of("institutionName", "Shapla School", "timezone", "Asia/Dhaka",
                "username", "Admin", "displayName", "প্রধান শিক্ষক", "email", "admin@example.test", "password", PASSWORD);
        ApiResponse created = anonymous.post("/setup", request);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().path("displayName").asString()).isEqualTo("প্রধান শিক্ষক");
        assertThat(anonymous.get("/institution").json().path("timezone").asString()).isEqualTo("Asia/Dhaka");

        ApiResponse again = anonymous.post("/setup", request);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("SETUP_COMPLETED");
        assertThat(anonymous.get("/setup").json().path("required").asBoolean()).isFalse();
    }

    @Test
    void lastActiveAdministratorCannotBeDisabledOrDemoted() {
        ApiClient admin = setupAdministrator();
        UUID adminId = UUID.fromString(admin.get("/auth/me").json().path("id").asString());

        ApiResponse disable = admin.put("/users/" + adminId + "/status", Map.of("status", "DISABLED"));
        assertThat(disable.status()).isEqualTo(409);
        assertThat(disable.code()).isEqualTo("LAST_ADMINISTRATOR");

        long revision = admin.get("/users/" + adminId).json().path("revision").asLong();
        ApiResponse demote = admin.put("/users/" + adminId, Map.of("displayName", "Administrator", "email", "admin@example.test",
                "roles", List.of("TEACHER"), "expectedRevision", revision));
        assertThat(demote.code()).isEqualTo("LAST_ADMINISTRATOR");

        TestUser second = createUser(admin, "second.admin", "second@example.test", "ADMINISTRATOR");
        assertThat(second.session().put("/users/" + adminId + "/status", Map.of("status", "DISABLED")).status()).isEqualTo(200);
    }

    @Test
    void usernamesAreCaseInsensitiveAndUnique() {
        ApiClient admin = setupAdministrator();
        createUser(admin, "Alice.Rahman", null, "STUDENT");

        ApiResponse duplicate = admin.post("/users", Map.of("username", "alice.rahman", "displayName", "Other",
                "roles", List.of("STUDENT"), "delivery", "TEMPORARY_PASSWORD"));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.code()).isEqualTo("USERNAME_TAKEN");

        ApiResponse invalid = admin.post("/users", Map.of("username", "no spaces", "displayName", "Other",
                "roles", List.of("STUDENT"), "delivery", "TEMPORARY_PASSWORD"));
        assertThat(invalid.json().path("fieldErrors").has("username")).isTrue();

        assertThat(login("ALICE.RAHMAN", PASSWORD).get("/auth/me").json().path("username").asString()).isEqualTo("alice.rahman");
    }

    @Test
    void studentEmailIsOptionalButStaffEmailIsRequired() {
        ApiClient admin = setupAdministrator();

        assertThat(admin.post("/users", Map.of("username", "student1", "displayName", "Student",
                "roles", List.of("STUDENT"), "delivery", "TEMPORARY_PASSWORD")).status()).isEqualTo(201);

        ApiResponse teacher = admin.post("/users", Map.of("username", "teacher1", "displayName", "Teacher",
                "roles", List.of("TEACHER"), "delivery", "TEMPORARY_PASSWORD"));
        assertThat(teacher.status()).isEqualTo(400);
        assertThat(teacher.json().path("fieldErrors").has("email")).isTrue();
    }

    @Test
    void passwordResetTokensAreHashedSingleUseAndExpire() {
        ApiClient admin = setupAdministrator();
        TestUser student = createUser(admin, "reset.me", "reset@example.test", "STUDENT");

        ApiClient anonymous = client();
        anonymous.get("/auth/csrf");
        assertThat(anonymous.post("/auth/password-reset/request", Map.of("usernameOrEmail", "RESET@example.test")).status()).isEqualTo(202);
        assertThat(anonymous.post("/auth/password-reset/request", Map.of("usernameOrEmail", "nobody")).status()).isEqualTo(202);
        assertThat(jdbc.sql("select token_hash from password_reset_tokens").query(String.class).single()).hasSize(64);

        insertToken("password_reset_tokens", student.id(), "valid-token", clock.instant().plus(Duration.ofMinutes(30)));
        insertToken("password_reset_tokens", student.id(), "expired-token", clock.instant().minusSeconds(1));
        String newPassword = "a brand new passphrase";

        ApiResponse reset = anonymous.post("/auth/password-reset/confirm", Map.of("token", "valid-token", "password", newPassword));
        ApiResponse resetAgain = anonymous.post("/auth/password-reset/confirm", Map.of("token", "valid-token", "password", newPassword));
        ApiResponse expired = anonymous.post("/auth/password-reset/confirm", Map.of("token", "expired-token", "password", newPassword));

        assertThat(reset.status()).isEqualTo(204);
        assertThat(resetAgain.status()).isEqualTo(400);
        assertThat(expired.code()).isEqualTo("INVALID_TOKEN");
        assertThat(student.session().get("/auth/me").status()).isEqualTo(401);
        assertThat(login("reset.me", newPassword).get("/auth/me").status()).isEqualTo(200);
    }

    @Test
    void invitationSetsFirstPasswordWithoutGrantingAnythingElse() {
        ApiClient admin = setupAdministrator();
        ApiResponse created = admin.post("/users", Map.of("username", "invited", "displayName", "Invited Teacher",
                "email", "invited@example.test", "roles", List.of("TEACHER"), "delivery", "INVITATION")).expect(201);
        UUID id = UUID.fromString(created.json().path("account").path("id").asString());
        assertThat(created.json().path("temporaryCredential").isNull()).isTrue();

        insertToken("invitations", id, "invite-token", clock.instant().plus(Duration.ofHours(72)));
        ApiClient anonymous = client();
        anonymous.get("/auth/csrf");
        assertThat(anonymous.post("/auth/invitations/accept", Map.of("token", "invite-token", "password", "short")).status()).isEqualTo(400);
        assertThat(anonymous.post("/auth/invitations/accept", Map.of("token", "invite-token", "password", PASSWORD)).status()).isEqualTo(204);

        assertThat(login("invited", PASSWORD).get("/auth/me").json().path("roles").toString()).contains("TEACHER");
    }

    @Test
    void disablingAnAccountInvalidatesItsSessions() {
        ApiClient admin = setupAdministrator();
        TestUser student = createUser(admin, "disable.me", null, "STUDENT");
        assertThat(student.session().get("/auth/me").status()).isEqualTo(200);

        admin.put("/users/" + student.id() + "/status", Map.of("status", "DISABLED")).expect(200);

        ApiResponse disabledSession = student.session().get("/auth/me");
        assertThat(disabledSession.status()).isEqualTo(401);
        assertThat(jdbc.sql("select count(*) from spring_session where principal_name = :id")
                .param("id", student.id().toString()).query(Long.class).single()).isZero();
        ApiClient retry = client();
        retry.get("/auth/csrf");
        assertThat(retry.post("/auth/login", Map.of("username", "disable.me", "password", PASSWORD)).code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void passwordChangeRevokesOtherSessionsButKeepsCurrent() {
        ApiClient admin = setupAdministrator();
        TestUser student = createUser(admin, "two.sessions", null, "STUDENT");
        ApiClient other = login("two.sessions", PASSWORD);

        student.session().put("/auth/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "another long passphrase")).expect(204);

        assertThat(student.session().get("/auth/me").status()).isEqualTo(200);
        assertThat(other.get("/auth/me").status()).isEqualTo(401);
    }

    @Test
    void assistedResetRevokesSessionsAndForcesPasswordChange() {
        ApiClient admin = setupAdministrator();
        TestUser teacher = createUser(admin, "teacher.one", "t1@example.test", "TEACHER");
        TestUser otherTeacher = createUser(admin, "teacher.two", "t2@example.test", "TEACHER");
        TestUser ownStudent = createUser(teacher.session(), "own.student", null, "STUDENT");
        TestUser unrelated = createUser(admin, "unrelated", null, "STUDENT");

        assertThat(teacher.session().post("/users/" + otherTeacher.id() + "/assisted-reset", null).status()).isEqualTo(404);
        assertThat(teacher.session().post("/users/" + unrelated.id() + "/assisted-reset", null).status()).isEqualTo(404);

        ApiResponse reset = teacher.session().post("/users/" + ownStudent.id() + "/assisted-reset", null).expect(200);
        assertThat(reset.header("Cache-Control")).contains("no-store");
        String temporary = reset.json().path("temporaryPassword").asString();
        assertThat(ownStudent.session().get("/auth/me").status()).isEqualTo(401);

        ApiClient student = login("own.student", temporary);
        assertThat(student.get("/auth/me").json().path("passwordChangeRequired").asBoolean()).isTrue();
        ApiResponse blocked = student.get("/users");
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.code()).isEqualTo("PASSWORD_CHANGE_REQUIRED");

        student.put("/auth/me/password", Map.of("currentPassword", temporary, "newPassword", PASSWORD)).expect(204);
        assertThat(student.get("/auth/me").json().path("passwordChangeRequired").asBoolean()).isFalse();
    }

    @Test
    void temporaryPasswordsExpireAfterTwentyFourHours() {
        ApiClient admin = setupAdministrator();
        ApiResponse created = admin.post("/users", Map.of("username", "late.student", "displayName", "Late",
                "roles", List.of("STUDENT"), "delivery", "TEMPORARY_PASSWORD")).expect(201);
        String temporary = created.json().path("temporaryCredential").path("temporaryPassword").asString();

        clock.advance(Duration.ofHours(25));
        ApiClient session = client();
        session.get("/auth/csrf");
        assertThat(session.post("/auth/login", Map.of("username", "late.student", "password", temporary)).status()).isEqualTo(401);
    }

    @Test
    void loginIsRateLimitedPerAccountAndAddress() {
        ApiClient admin = setupAdministrator();
        createUser(admin, "guessed", null, "STUDENT");
        ApiClient attacker = client();
        attacker.get("/auth/csrf");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(attacker.post("/auth/login", Map.of("username", "guessed", "password", "wrong password!")).status()).isEqualTo(401);
        }
        ApiResponse limited = attacker.post("/auth/login", Map.of("username", "guessed", "password", PASSWORD));
        assertThat(limited.status()).isEqualTo(429);

        clock.advance(Duration.ofMinutes(16));
        assertThat(attacker.post("/auth/login", Map.of("username", "guessed", "password", PASSWORD)).status()).isEqualTo(200);
    }

    @Test
    void mutationsRequireCsrfTokenAndSessionCookieIsHardened() {
        setupAdministrator();
        ApiClient browser = client();
        ApiResponse withoutToken = browser.post("/auth/login", Map.of("username", "admin", "password", PASSWORD));
        assertThat(withoutToken.status()).isEqualTo(403);
        assertThat(withoutToken.code()).isEqualTo("CSRF_REJECTED");

        browser.get("/auth/csrf");
        String tokenBeforeLogin = browser.csrfToken().orElseThrow();
        ApiResponse login = browser.post("/auth/login", Map.of("username", "admin", "password", PASSWORD)).expect(200);
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
    void teachersOnlySeeAndCreateStudentsInTheirScope() {
        ApiClient admin = setupAdministrator();
        TestUser teacher = createUser(admin, "scoped.teacher", "scoped@example.test", "TEACHER");
        TestUser own = createUser(teacher.session(), "mine", null, "STUDENT");
        TestUser notMine = createUser(admin, "not.mine", null, "STUDENT");

        ApiResponse listed = teacher.session().get("/users");
        assertThat(listed.json().path("items").findValuesAsString("username")).containsExactly("mine");
        assertThat(teacher.session().get("/users/" + own.id()).status()).isEqualTo(200);
        assertThat(teacher.session().get("/users/" + notMine.id()).status()).isEqualTo(404);
        assertThat(teacher.session().post("/users", Map.of("username", "new.teacher", "displayName", "New", "email", "n@example.test",
                "roles", List.of("TEACHER"), "delivery", "TEMPORARY_PASSWORD")).status()).isEqualTo(403);
        assertThat(own.session().get("/users").status()).isEqualTo(403);
    }

    private void insertToken(String table, UUID userId, String rawToken, Instant expiresAt) {
        jdbc.sql("insert into " + table + " (id, user_id, token_hash, expires_at, created_at) values (:id, :user, :hash, :expires, :now)")
                .param("id", UUID.randomUUID())
                .param("user", userId)
                .param("hash", sha256(rawToken))
                .param("expires", Timestamp.from(expiresAt))
                .param("now", Timestamp.from(clock.instant()))
                .update();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
