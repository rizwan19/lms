package com.saasresearcher.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Owner setup: REQUIRED → OWNER_VERIFICATION_PENDING → ACTIVE, with no privileged session before verification. */
class WorkspaceSetupIT extends PostgresIntegrationTest {

    private static final Map<String, String> SETUP = Map.of("workspaceName", "Shapla School", "timezone", "Asia/Dhaka", "username", "Owner",
            "displayName", "প্রধান শিক্ষক", "email", "Owner@Example.test", "password", PASSWORD);

    @Test
    void setupCreatesAnUnverifiedOwnerAndActivatesOnlyAfterVerification() {
        ApiClient anonymous = client();
        assertThat(anonymous.get("/setup").json().asString()).isEqualTo("REQUIRED");

        ApiResponse started = anonymous.post("/setup", SETUP).expect(202);
        assertThat(started.json().path("nextAction").asString()).isEqualTo("VERIFY_EMAIL");
        assertThat(started.text()).doesNotContain("token", "password");
        assertThat(anonymous.get("/auth/me").status()).as("setup never signs anyone in").isEqualTo(401);
        assertThat(anonymous.get("/setup").json().asString()).isEqualTo("OWNER_VERIFICATION_PENDING");
        assertThat(anonymous.get("/workspace").json().path("timezone").asString()).isEqualTo("Asia/Dhaka");

        ApiClient owner = login("owner", PASSWORD);
        JsonAssert me = new JsonAssert(owner.get("/auth/me").expect(200));
        assertThat(me.text("emailVerified")).isEqualTo("false");
        assertThat(me.text("teacherStatus")).isEqualTo("PENDING");
        ApiResponse blocked = owner.get("/workspace/users");
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.code()).isEqualTo("EMAIL_UNVERIFIED");
        assertThat(client().post("/auth/register", Map.of("username", "early", "displayName", "Early", "email", "early@example.test",
                "password", PASSWORD, "role", "STUDENT")).code()).as("registration waits for an active workspace").isEqualTo("REGISTRATION_CLOSED");

        String token = tokenFor("owner@example.test");
        anonymous.post("/auth/email/verify", Map.of("token", token)).expect(204);
        assertThat(anonymous.post("/auth/email/verify", Map.of("token", token)).code()).as("one use").isEqualTo("INVALID_TOKEN");

        assertThat(anonymous.get("/setup").json().asString()).isEqualTo("ACTIVE");
        JsonAssert verified = new JsonAssert(owner.get("/auth/me").expect(200));
        assertThat(verified.text("teacherStatus")).isEqualTo("APPROVED");
        assertThat(verified.text("displayName")).isEqualTo("প্রধান শিক্ষক");
        assertThat(owner.get("/auth/me").json().path("capabilities").path("canAdministerWorkspace").asBoolean()).isTrue();
        assertThat(owner.get("/workspace/users").status()).isEqualTo(200);
        assertThat(jdbc.sql("select count(*) from audit_events where action = 'setup.owner-verified'").query(Long.class).single()).isEqualTo(1);

        assertThat(anonymous.post("/setup", SETUP).code()).isEqualTo("SETUP_COMPLETED");
    }

    @Test
    void concurrentSetupsCreateExactlyOneOwner() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                String username = "owner" + i;
                ApiClient session = client();
                results.add(pool.submit(() -> {
                    barrier.await();
                    return session.post("/setup", Map.of("workspaceName", "School", "timezone", "UTC", "username", username, "displayName", "Owner",
                            "email", username + "@example.test", "password", PASSWORD)).status();
                }));
            }
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(202, 409);
        }
        assertThat(jdbc.sql("select count(*) from users").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void resendInvalidatesTheEarlierOwnerLink() {
        ApiClient anonymous = client();
        anonymous.post("/setup", SETUP).expect(202);
        String first = tokenFor("owner@example.test");
        ApiClient owner = login("owner", PASSWORD);
        owner.post("/auth/email/resend", null).expect(202);
        String second = tokenFor("owner@example.test");

        assertThat(anonymous.post("/auth/email/verify", Map.of("token", first)).code()).isEqualTo("INVALID_TOKEN");
        anonymous.post("/auth/email/verify", Map.of("token", second)).expect(204);
        assertThat(anonymous.get("/setup").json().asString()).isEqualTo("ACTIVE");
    }

    @Test
    void verificationLinksExpireAfterADay() {
        client().post("/setup", SETUP).expect(202);
        String token = tokenFor("owner@example.test");
        clock.advance(java.time.Duration.ofHours(25));
        assertThat(client().post("/auth/email/verify", Map.of("token", token)).code()).isEqualTo("INVALID_TOKEN");
        assertThat(client().get("/setup").json().asString()).isEqualTo("OWNER_VERIFICATION_PENDING");
    }

    @Test
    void setupRequiresConfiguredEmailDelivery() {
        email.configured(false);
        assertThat(client().post("/setup", SETUP).code()).isEqualTo("EMAIL_NOT_CONFIGURED");
        assertThat(jdbc.sql("select count(*) from users").query(Long.class).single()).isZero();
    }

    private record JsonAssert(ApiResponse response) {

        String text(String field) {
            return response.json().path(field).asString();
        }
    }
}
