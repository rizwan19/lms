package com.saasresearcher.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.common.TeacherStatus;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exactly two roles; workspace powers come from current approval, never from the role alone or a stale session. */
class TwoRoleAuthorizationIT extends PostgresIntegrationTest {

    private TestUser owner;

    @BeforeEach
    void workspace() {
        owner = setupOwner();
    }

    @Test
    void onlyTwoRolesExist() {
        assertThat(jdbc.sql("select count(*) from information_schema.tables where table_name in ('user_roles', 'classes', 'course_teachers')")
                .query(Long.class).single()).isZero();
        ApiResponse admin = client().post("/auth/register", Map.of("username", "sneaky", "displayName", "Sneaky", "email", "sneaky@example.test",
                "password", PASSWORD, "role", "ADMINISTRATOR"));
        assertThat(admin.status()).isEqualTo(400);
    }

    @Test
    void approvedTeachersAdministerAccountsButStudentsAndPendingTeachersCannot() {
        TestUser student = registerStudent("student");
        TestUser pending = registerTeacher("pending");
        assertThat(owner.session().get("/workspace/users?role=STUDENT").json().path("items").findValuesAsString("username")).containsExactly("student");
        assertThat(student.session().get("/workspace/users").status()).isEqualTo(403);
        assertThat(pending.session().get("/workspace/users").code()).isEqualTo("TEACHER_APPROVAL_REQUIRED");
        assertThat(student.session().put("/workspace", Map.of("name", "Mine", "timezone", "UTC", "certificateVerification", false,
                "verificationShowsStudent", false, "expectedRevision", 0)).status()).isEqualTo(403);
        assertThat(pending.session().get("/admin/system-status").code()).isEqualTo("TEACHER_APPROVAL_REQUIRED");
        assertThat(owner.session().get("/admin/system-status").status()).isEqualTo(200);
    }

    @Test
    void suspensionTakesEffectOnTheExistingSessionAndReactivationRestoresAccess() {
        TestUser student = registerStudent("suspend.me");
        long revision = owner.session().get("/workspace/users/" + student.id()).json().path("revision").asLong();
        owner.session().put("/workspace/users/" + student.id() + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision)).expect(200);
        assertThat(student.session().get("/auth/me").status()).isEqualTo(401);
        assertThat(jdbc.sql("select count(*) from spring_session where principal_name = :id").param("id", student.id().toString())
                .query(Long.class).single()).isZero();
        assertThat(client().post("/auth/login", Map.of("username", "suspend.me", "password", PASSWORD)).code()).isEqualTo("INVALID_CREDENTIALS");

        revision = owner.session().get("/workspace/users/" + student.id()).json().path("revision").asLong();
        owner.session().put("/workspace/users/" + student.id() + "/status", Map.of("status", "ACTIVE", "expectedRevision", revision)).expect(200);
        login("suspend.me", PASSWORD);
    }

    @Test
    void nobodySuspendsThemselvesSoTheLastApprovedTeacherRemains() {
        long revision = owner.session().get("/workspace/users/" + owner.id()).json().path("revision").asLong();
        ApiResponse self = owner.session().put("/workspace/users/" + owner.id() + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision));
        assertThat(self.status()).isEqualTo(409);
        assertThat(self.code()).isEqualTo("SELF_SUSPENSION");
    }

    @Test
    void twoTeachersSuspendingEachOtherLeaveOneApprovedTeacher() throws Exception {
        TestUser peer = approvedTeacher("peer", owner);
        long ownerRevision = owner.session().get("/workspace/users/" + owner.id()).json().path("revision").asLong();
        long peerRevision = owner.session().get("/workspace/users/" + peer.id()).json().path("revision").asLong();
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            results.add(pool.submit(() -> {
                barrier.await();
                return owner.session().put("/workspace/users/" + peer.id() + "/status", Map.of("status", "SUSPENDED", "expectedRevision", peerRevision))
                        .status();
            }));
            results.add(pool.submit(() -> {
                barrier.await();
                return peer.session().put("/workspace/users/" + owner.id() + "/status", Map.of("status", "SUSPENDED", "expectedRevision", ownerRevision))
                        .status();
            }));
            assertThat(results.get(0).get() == 200 ^ results.get(1).get() == 200).as("exactly one suspension wins").isTrue();
        }
        assertThat(jdbc.sql("select count(*) from users where role = :role and teacher_status = :approval and status = :status")
                .param("role", Role.TEACHER.code()).param("approval", TeacherStatus.APPROVED.code()).param("status", AccountStatus.ACTIVE.code())
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void approvedTeachersSendRecoveryEmailsButNeverSetPasswords() {
        TestUser student = registerStudent("forgetful");
        email.reset();
        owner.session().post("/workspace/users/" + student.id() + "/password-reset", null).expect(202);
        String token = tokenFor("forgetful@example.test");
        client().post("/auth/password-reset/confirm", Map.of("token", token, "password", "chosen by the owner only")).expect(204);
        login("forgetful", "chosen by the owner only");
        assertThat(jdbc.sql("select count(*) from audit_events where action = 'account.recovery-sent'").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void workspaceSettingsAreApprovedTeacherOnlyAndRevisionChecked() {
        TestUser peer = approvedTeacher("peer", owner);
        long revision = client().get("/workspace").json().path("revision").asLong();
        peer.session().put("/workspace", Map.of("name", "Renamed", "timezone", "Asia/Dhaka", "certificateVerification", false,
                "verificationShowsStudent", false, "expectedRevision", revision)).expect(200);
        assertThat(owner.session().put("/workspace", Map.of("name", "Stale", "timezone", "UTC", "certificateVerification", false,
                "verificationShowsStudent", false, "expectedRevision", revision)).code()).isEqualTo("REVISION_CONFLICT");
        assertThat(client().get("/workspace").json().path("name").asString()).isEqualTo("Renamed");
    }
}
