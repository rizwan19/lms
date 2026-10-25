package com.saasresearcher.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.common.TeacherStatus;
import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Public signup for both roles with mandatory, token-bound email verification. */
class PublicRegistrationIT extends PostgresIntegrationTest {

    @BeforeEach
    void activeWorkspace() {
        setupOwner();
    }

    @Test
    void bothRolesRegisterUnverifiedAndGetNoSessionOrToken() {
        for (String role : List.of("STUDENT", "TEACHER")) {
            ApiResponse receipt = register("new." + role.toLowerCase(), "  New." + role + "@Example.TEST ", role).expect(201);
            assertThat(receipt.json().path("nextAction").asString()).isEqualTo("VERIFY_EMAIL");
            assertThat(receipt.headers().getOrDefault("set-cookie", List.of())).noneMatch(cookie -> cookie.startsWith("LEARNING_SESSION="));
            assertThat(receipt.text()).doesNotContain("token");
        }
        assertThat(jdbc.sql("select email from users where username = 'new.student'").query(String.class).single()).isEqualTo("new.student@example.test");
        assertThat(TeacherStatus.fromCode(jdbc.sql("select teacher_status from users where username = 'new.teacher'").query(Short.class).single()))
                .isEqualTo(TeacherStatus.PENDING);
        assertThat(jdbc.sql("select count(*) from users where email_verified_at is not null and username like 'new.%'").query(Long.class).single()).isZero();
        runJobs();
        assertThat(jdbc.sql("select count(*) from background_jobs where payload::text like '%token%'").query(Long.class).single()).isZero();
    }

    @Test
    void unverifiedAccountsCanOnlyVerifyResendOrSignOut() {
        register("waiting", "waiting@example.test", "STUDENT").expect(201);
        ApiClient session = login("waiting", PASSWORD);
        assertThat(session.get("/auth/me").json().path("capabilities").path("canLearn").asBoolean()).isFalse();
        ApiResponse denied = session.get("/me/enrollments");
        assertThat(denied.status()).isEqualTo(403);
        assertThat(denied.code()).isEqualTo("EMAIL_UNVERIFIED");
        assertThat(session.get("/catalog/courses").status()).isEqualTo(200);

        session.post("/auth/email/verify", Map.of("token", tokenFor("waiting@example.test"))).expect(204);
        assertThat(session.get("/me/enrollments").status()).isEqualTo(200);
        assertThat(session.get("/auth/me").json().path("capabilities").path("canLearn").asBoolean()).isTrue();
    }

    @Test
    void usernamesAndEmailsAreNormalizedAndUnique() {
        register("Alice.Rahman", "alice@example.test", "STUDENT").expect(201);
        assertThat(register("alice.rahman", "other@example.test", "STUDENT").code()).isEqualTo("USERNAME_TAKEN");
        assertThat(register("alice2", " ALICE@example.test", "TEACHER").code()).isEqualTo("EMAIL_TAKEN");
        assertThat(register("no spaces", "x@example.test", "STUDENT").json().path("fieldErrors").has("username")).isTrue();
        assertThat(register("short.pw", "pw@example.test", "STUDENT", "short").json().path("fieldErrors").has("password")).isTrue();
        ApiResponse noRole = client().post("/auth/register", Map.of("username", "norole", "displayName", "No role", "email", "n@example.test",
                "password", PASSWORD));
        assertThat(noRole.status()).isEqualTo(400);
    }

    @Test
    void concurrentDuplicateSignupsCreateOneAccount() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                ApiClient session = client();
                results.add(pool.submit(() -> {
                    barrier.await();
                    return session.post("/auth/register", Map.of("username", "racer", "displayName", "Racer", "email", "racer@example.test",
                            "password", PASSWORD, "role", "STUDENT")).status();
                }));
            }
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.sql("select count(*) from users where username = 'racer'").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void tokensAreHashedPurposeBoundSingleUseAndExpire() {
        register("tokens", "tokens@example.test", "STUDENT").expect(201);
        String token = tokenFor("tokens@example.test");
        assertThat(jdbc.sql("select token_hash from identity_tokens").query(String.class).list()).allMatch(hash -> hash.length() == 64)
                .noneMatch(hash -> hash.equals(token));
        assertThat(client().post("/auth/password-reset/confirm", Map.of("token", token, "password", "another long passphrase")).code())
                .as("verification tokens cannot reset passwords").isEqualTo("INVALID_TOKEN");

        clock.advance(Duration.ofHours(24).plusSeconds(1));
        assertThat(client().post("/auth/email/verify", Map.of("token", token)).code()).isEqualTo("INVALID_TOKEN");

        ApiClient session = login("tokens", PASSWORD);
        session.post("/auth/email/resend", null).expect(202);
        String fresh = tokenFor("tokens@example.test");
        client().post("/auth/email/verify", Map.of("token", fresh)).expect(204);
        assertThat(client().post("/auth/email/verify", Map.of("token", fresh)).code()).isEqualTo("INVALID_TOKEN");
        assertThat(session.post("/auth/email/resend", null).code()).isEqualTo("ALREADY_VERIFIED");
    }

    @Test
    void resendInvalidatesTheEarlierLinkAndIsRateLimited() {
        register("resender", "resender@example.test", "STUDENT").expect(201);
        String first = tokenFor("resender@example.test");
        ApiClient session = login("resender", PASSWORD);
        session.post("/auth/email/resend", null).expect(202);
        String second = tokenFor("resender@example.test");
        assertThat(client().post("/auth/email/verify", Map.of("token", first)).code()).isEqualTo("INVALID_TOKEN");

        for (int i = 0; i < 4; i++) {
            session.post("/auth/email/resend", null).expect(202);
        }
        assertThat(session.post("/auth/email/resend", null).status()).isEqualTo(429);
        runJobs();
        client().post("/auth/email/verify", Map.of("token", tokenFor("resender@example.test"))).expect(204);
        assertThat(second).isNotNull();
    }

    @Test
    void smtpFailureKeepsTheAccountUnverifiedAndARetryDeliversAWorkingLink() {
        email.failing(true);
        register("retry", "retry@example.test", "TEACHER").expect(201);
        runJobs();
        assertThat(email.sent()).extracting(EmailMessage::to).doesNotContain("retry@example.test");
        assertThat(jdbc.sql("select email_verified_at is null from users where username = 'retry'").query(Boolean.class).single()).isTrue();

        email.failing(false);
        clock.advance(Duration.ofMinutes(1));
        client().post("/auth/email/verify", Map.of("token", tokenFor("retry@example.test"))).expect(204);
        assertThat(jdbc.sql("select count(*) from teacher_applications").query(Long.class).single())
                .as("verification opens the teacher application exactly once").isEqualTo(1);
    }

    @Test
    void registrationNeedsEmailDelivery() {
        email.configured(false);
        assertThat(register("nomail", "nomail@example.test", "STUDENT").code()).isEqualTo("EMAIL_NOT_CONFIGURED");
    }

    @Test
    void passwordResetWorksOnlyThroughTheVerifiedEmail() {
        register("unverified", "unverified@example.test", "STUDENT").expect(201);
        TestUser verified = registerStudent("verified");
        email.reset();
        ApiClient anonymous = client();
        anonymous.post("/auth/password-reset/request", Map.of("usernameOrEmail", "unverified")).expect(202);
        anonymous.post("/auth/password-reset/request", Map.of("usernameOrEmail", "VERIFIED@example.test")).expect(202);
        anonymous.post("/auth/password-reset/request", Map.of("usernameOrEmail", "nobody")).expect(202);
        runJobs();
        assertThat(email.sent()).extracting(EmailMessage::to).containsExactly("verified@example.test");

        String token = tokenFor("verified@example.test");
        anonymous.post("/auth/password-reset/confirm", Map.of("token", token, "password", "a brand new passphrase")).expect(204);
        assertThat(anonymous.post("/auth/password-reset/confirm", Map.of("token", token, "password", "a brand new passphrase")).code())
                .isEqualTo("INVALID_TOKEN");
        assertThat(verified.session().get("/auth/me").status()).as("reset revokes sessions").isEqualTo(401);
        login("verified", "a brand new passphrase");
    }

    private ApiResponse register(String username, String address, String role) {
        return register(username, address, role, PASSWORD);
    }

    private ApiResponse register(String username, String address, String role, String password) {
        return client().post("/auth/register", Map.of("username", username, "displayName", "User " + username, "email", address,
                "password", password, "role", role));
    }
}
