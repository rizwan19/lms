package com.saasresearcher.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.identity.api.ApplicationStatus;
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
import tools.jackson.databind.JsonNode;

/** Peer review of teacher accounts: verified applicants only, first decision wins, no self-review. */
class TeacherApprovalIT extends PostgresIntegrationTest {

    private TestUser owner;

    @BeforeEach
    void workspace() {
        owner = setupOwner();
    }

    @Test
    void verificationOpensOneApplicationAndNotifiesApprovedTeachers() {
        ApiClient anonymous = client();
        anonymous.post("/auth/register", Map.of("username", "applicant", "displayName", "Applicant", "email", "applicant@example.test",
                "password", PASSWORD, "role", "TEACHER")).expect(201);
        assertThat(inbox(owner)).as("unverified teachers are not in the queue").isEmpty();

        anonymous.post("/auth/email/verify", Map.of("token", tokenFor("applicant@example.test"))).expect(204);
        runJobs();
        assertThat(inbox(owner)).hasSize(1);
        assertThat(owner.session().get("/notifications").json().path("items").findValuesAsString("type")).contains("teacher-application.submitted");
        assertThat(email.sent()).extracting(EmailMessage::to).contains("owner@example.test");

        ApiClient applicant = login("applicant", PASSWORD);
        assertThat(applicant.get("/me/teacher-application").json().path("status").asString()).isEqualTo("PENDING");
        assertThat(applicant.post("/me/teacher-applications", null).expect(201).json().path("id").asString())
                .as("a second submission returns the pending application").isEqualTo(inbox(owner).get(0).path("id").asString());
        assertThat(jdbc.sql("select count(*) from teacher_applications").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void pendingTeachersCanLearnAndDraftButNotReviewOrAdminister() {
        TestUser pending = registerTeacher("pending");
        JsonNode me = pending.session().get("/auth/me").json();
        assertThat(me.path("capabilities").path("canLearn").asBoolean()).isTrue();
        assertThat(me.path("capabilities").path("canPublishCourses").asBoolean()).isFalse();
        assertThat(me.path("capabilities").path("canAdministerWorkspace").asBoolean()).isFalse();

        ApiResponse review = pending.session().get("/workspace/teacher-applications");
        assertThat(review.status()).isEqualTo(403);
        assertThat(review.code()).isEqualTo("TEACHER_APPROVAL_REQUIRED");
        assertThat(pending.session().get("/workspace/users").code()).isEqualTo("TEACHER_APPROVAL_REQUIRED");
        assertThat(pending.session().post("/courses", Map.of("title", "My draft")).status()).isEqualTo(201);
    }

    @Test
    void applicantsCannotReviewThemselvesAndDecisionsResolveOnce() {
        TestUser peer = approvedTeacher("peer", owner);
        TestUser applicant = registerTeacher("applicant");
        JsonNode application = inbox(owner).get(0);
        String path = "/workspace/teacher-applications/" + application.path("id").asString() + "/decision";
        Map<String, Object> approve = Map.of("expectedRevision", application.path("revision").asLong(), "approve", true);

        assertThat(applicant.session().post(path, approve).status()).isEqualTo(403);
        owner.session().post(path, approve).expect(200);
        assertThat(owner.session().post(path, approve).json().path("status").asString()).as("same decision again is harmless").isEqualTo("APPROVED");
        ApiResponse competing = peer.session().post(path, Map.of("expectedRevision", application.path("revision").asLong(), "approve", false));
        assertThat(competing.status()).isEqualTo(409);
        assertThat(competing.code()).isEqualTo("APPLICATION_ALREADY_DECIDED");

        assertThat(applicant.session().get("/auth/me").json().path("capabilities").path("canPublishCourses").asBoolean())
                .as("approval applies to the existing session at once").isTrue();
        runJobs();
        assertThat(applicant.session().get("/notifications").json().path("items").findValuesAsString("title"))
                .containsExactly("Your teacher application was approved");
    }

    @Test
    void concurrentPeerDecisionsProduceOneTransitionAndOneNotice() throws Exception {
        TestUser peer = approvedTeacher("peer", owner);
        TestUser applicant = registerTeacher("raced");
        JsonNode application = inbox(owner).get(0);
        String path = "/workspace/teacher-applications/" + application.path("id").asString() + "/decision";
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (TestUser reviewer : List.of(owner, peer)) {
                boolean approve = reviewer == owner;
                results.add(pool.submit(() -> {
                    barrier.await();
                    return reviewer.session().post(path, Map.of("expectedRevision", application.path("revision").asLong(), "approve", approve)).status();
                }));
            }
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 409);
        }
        runJobs();
        assertThat(jdbc.sql("select count(*) from teacher_applications where status <> :pending and applicant_id = :id")
                .param("pending", ApplicationStatus.PENDING.code()).param("id", applicant.id())
                .query(Long.class).single()).isEqualTo(1);
        assertThat(applicant.session().get("/notifications").json().path("items")).hasSize(1);
        assertThat(jdbc.sql("select count(*) from audit_events where action in ('teacher-application.approved', 'teacher-application.rejected')"
                + " and aggregate_id = :id").param("id", java.util.UUID.fromString(application.path("id").asString())).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void rejectedTeachersMayReapplyAfterADay() {
        TestUser applicant = registerTeacher("rejected");
        JsonNode application = inbox(owner).get(0);
        owner.session().post("/workspace/teacher-applications/" + application.path("id").asString() + "/decision",
                Map.of("expectedRevision", application.path("revision").asLong(), "approve", false, "reason", "Add your teaching background")).expect(200);
        JsonNode mine = applicant.session().get("/me/teacher-application").json();
        assertThat(mine.path("status").asString()).isEqualTo("REJECTED");
        assertThat(mine.path("reason").asString()).isEqualTo("Add your teaching background");

        assertThat(applicant.session().post("/me/teacher-applications", null).code()).isEqualTo("APPLICATION_COOLDOWN");
        clock.advance(Duration.ofHours(24));
        assertThat(applicant.session().post("/me/teacher-applications", null).expect(201).json().path("status").asString()).isEqualTo("PENDING");
        assertThat(applicant.session().get("/auth/me").json().path("teacherStatus").asString()).isEqualTo("PENDING");
        assertThat(inbox(owner)).hasSize(1);
    }

    @Test
    void studentsHaveNoApplication() {
        TestUser student = registerStudent("learner");
        assertThat(student.session().post("/me/teacher-applications", null).status()).isEqualTo(403);
        assertThat(student.session().get("/me/teacher-application").status()).isEqualTo(404);
    }

    @Test
    void suspendedReviewersCannotDecide() {
        TestUser peer = approvedTeacher("peer", owner);
        registerTeacher("waiting");
        JsonNode application = inbox(owner).get(0);
        long revision = owner.session().get("/workspace/users/" + peer.id()).json().path("revision").asLong();
        owner.session().put("/workspace/users/" + peer.id() + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision)).expect(200);
        ApiClient again = client();
        assertThat(again.post("/auth/login", Map.of("username", "peer", "password", PASSWORD)).code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(peer.session().post("/workspace/teacher-applications/" + application.path("id").asString() + "/decision",
                Map.of("expectedRevision", application.path("revision").asLong(), "approve", true)).status()).isEqualTo(401);
    }

    private List<JsonNode> inbox(TestUser reviewer) {
        return reviewer.session().get("/workspace/teacher-applications").expect(200).json().path("items").valueStream().toList();
    }
}
