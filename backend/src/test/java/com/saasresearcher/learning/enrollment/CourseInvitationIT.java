package com.saasresearcher.learning.enrollment;

import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.enrollment.api.GrantSource;
import com.saasresearcher.learning.enrollment.api.InvitationStatus;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.EnrollmentFixtures;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Author invitations preauthorize enrollment for the verified account with the matching email; acceptance is explicit. */
class CourseInvitationIT extends PostgresIntegrationTest {

    private TestUser owner;
    private TestUser author;
    private UUID courseId;

    @BeforeEach
    void course() {
        owner = setupOwner();
        author = approvedTeacher("author", owner);
        courseId = UUID.fromString(publishedCourse(author.session(), "Physics", List.of(lesson("Motion", "", true))).path("courseId").asString());
    }

    @Test
    void existingVerifiedAccountAcceptsAndIsEnrolledWithoutAnotherApproval() {
        TestUser learner = registerStudent("invitee");
        JsonNode invitation = invite("Invitee@Example.test").expect(201).json();
        assertThat(invitation.path("email").asString()).isEqualTo("invitee@example.test");
        assertThat(invitation.toString()).doesNotContain("token");
        String token = tokenFor("invitee@example.test");
        assertThat(learner.session().get("/me/enrollments").json()).as("the link alone grants nothing").isEmpty();

        JsonNode accepted = learner.session().post("/course-invitations/accept", Map.of("token", token)).expect(200).json();
        assertThat(learner.session().get("/me/enrollments/" + accepted.path("enrollmentId").asString() + "/content").status()).isEqualTo(200);
        assertThat(GrantSource.fromCode(jdbc.sql("select grant_source from course_enrollments").query(Short.class).single())).isEqualTo(GrantSource.INVITATION);
        assertThat(learner.session().post("/course-invitations/accept", Map.of("token", token)).code()).isEqualTo("INVITATION_INVALID");
        assertThat(jdbc.sql("select count(*) from background_jobs where payload::text like :token").param("token", "%" + token + "%")
                .query(Long.class).single()).isZero();
    }

    @Test
    void newAccountsSignUpVerifyAndThenAccept() {
        invite("newcomer@example.test").expect(201);
        String token = tokenFor("newcomer@example.test");
        TestUser newcomer = registerStudent("newcomer");
        ApiResponse accepted = newcomer.session().post("/course-invitations/accept", Map.of("token", token));
        assertThat(accepted.status()).isEqualTo(200);
    }

    @Test
    void theRecipientEmailMustMatchAndBeVerified() {
        invite("right@example.test").expect(201);
        String token = tokenFor("right@example.test");
        TestUser wrong = registerStudent("wrong");
        ApiResponse mismatch = wrong.session().post("/course-invitations/accept", Map.of("token", token));
        assertThat(mismatch.status()).isEqualTo(403);
        assertThat(mismatch.code()).isEqualTo("INVITATION_EMAIL_MISMATCH");

        client().post("/auth/register", Map.of("username", "right", "displayName", "Right", "email", "right@example.test", "password", PASSWORD,
                "role", "STUDENT")).expect(201);
        ApiClient unverified = login("right", PASSWORD);
        assertThat(unverified.post("/course-invitations/accept", Map.of("token", token)).code()).isEqualTo("EMAIL_UNVERIFIED");
        assertThat(InvitationStatus.fromCode(jdbc.sql("select status from course_invitations").query(Short.class).single())).as("token unused")
                .isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void invitationsExpireAfterSevenDaysAndNewerOnesReplacePendingOnes() {
        TestUser learner = registerStudent("late");
        invite("late@example.test").expect(201);
        String first = tokenFor("late@example.test");
        invite("late@example.test").expect(201);
        String second = tokenFor("late@example.test");
        assertThat(learner.session().post("/course-invitations/accept", Map.of("token", first)).code()).isEqualTo("INVITATION_INVALID");
        assertThat(jdbc.sql("select count(*) from course_invitations where status = :status").param("status", InvitationStatus.REVOKED.code()).query(Long.class).single()).isEqualTo(1);

        clock.advance(Duration.ofDays(7));
        assertThat(learner.session().post("/course-invitations/accept", Map.of("token", second)).code()).isEqualTo("INVITATION_INVALID");
    }

    @Test
    void revokedInvitationsAndSuspendedAuthorsCannotGrant() {
        TestUser learner = registerStudent("revoked");
        String invitationId = invite("revoked@example.test").json().path("id").asString();
        String token = tokenFor("revoked@example.test");
        TestUser other = approvedTeacher("other", owner);
        assertThat(other.session().post("/course-invitations/" + invitationId + "/revoke", null).status()).isEqualTo(404);
        author.session().post("/course-invitations/" + invitationId + "/revoke", null).expect(200);
        assertThat(learner.session().post("/course-invitations/accept", Map.of("token", token)).code()).isEqualTo("INVITATION_INVALID");

        invite("revoked@example.test").expect(201);
        String fresh = tokenFor("revoked@example.test");
        long revision = owner.session().get("/workspace/users/" + author.id()).json().path("revision").asLong();
        owner.session().put("/workspace/users/" + author.id() + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision)).expect(200);
        assertThat(learner.session().post("/course-invitations/accept", Map.of("token", fresh)).code()).isEqualTo("INVITATION_UNAVAILABLE");
        assertThat(jdbc.sql("select count(*) from course_invitations where status = :status").param("status", InvitationStatus.PENDING.code()).query(Long.class).single()).as("token unused")
                .isEqualTo(1);
    }

    @Test
    void acceptanceWithAnActiveEnrollmentIsHarmlessAndResolvesPendingRequests() {
        TestUser learner = registerStudent("both");
        JsonNode request = learner.session().post("/courses/" + courseId + "/access-requests", null).expect(200).json();
        invite("both@example.test").expect(201);
        learner.session().post("/course-invitations/accept", Map.of("token", tokenFor("both@example.test"))).expect(200);
        assertThat(learner.session().get("/me/access-requests").json().get(0).path("status").asString()).isEqualTo("APPROVED");
        assertThat(author.session().post("/access-requests/" + request.path("id").asString() + "/decision",
                Map.of("expectedRevision", request.path("revision").asLong(), "approve", false)).code()).isEqualTo("REQUEST_ALREADY_DECIDED");

        invite("both@example.test").expect(201);
        learner.session().post("/course-invitations/accept", Map.of("token", tokenFor("both@example.test"))).expect(200);
        assertThat(jdbc.sql("select count(*) from course_enrollments").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void concurrentRequestApprovalAndInvitationAcceptanceYieldOneEnrollment() throws Exception {
        TestUser learner = registerStudent("racer");
        JsonNode request = learner.session().post("/courses/" + courseId + "/access-requests", null).expect(200).json();
        invite("racer@example.test").expect(201);
        String token = tokenFor("racer@example.test");
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            results.add(pool.submit(() -> {
                barrier.await();
                return author.session().post("/access-requests/" + request.path("id").asString() + "/decision",
                        Map.of("expectedRevision", request.path("revision").asLong(), "approve", true)).status();
            }));
            results.add(pool.submit(() -> {
                barrier.await();
                return learner.session().post("/course-invitations/accept", Map.of("token", token)).status();
            }));
            assertThat(results.get(1).get()).isEqualTo(200);
            assertThat(results.get(0).get()).isIn(200, 409);
        }
        assertThat(jdbc.sql("select count(*) from course_enrollments").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void suspensionRevokesPendingInvitesAndStaleInvitationsCannotUndoIt() {
        TestUser learner = registerStudent("stale");
        UUID enrollmentId = EnrollmentFixtures.enroll(author.session(), learner.session(), courseId);
        invite("stale@example.test").expect(201);
        String pending = tokenFor("stale@example.test");
        long revision = author.session().get("/courses/" + courseId + "/enrollments").json().path("items").get(0).path("revision").asLong();
        author.session().put("/enrollments/" + enrollmentId + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision)).expect(200);
        assertThat(learner.session().post("/course-invitations/accept", Map.of("token", pending)).code()).isEqualTo("INVITATION_INVALID");

        clock.advance(Duration.ofMinutes(1));
        invite("stale@example.test").expect(201);
        learner.session().post("/course-invitations/accept", Map.of("token", tokenFor("stale@example.test"))).expect(200);
        assertThat(learner.session().get("/me/enrollments/" + enrollmentId + "/content").status()).as("a fresh invitation restores").isEqualTo(200);
    }

    @Test
    void bulkInvitesNormalizeDeduplicateAndCapAtOneHundred() {
        List<String> emails = new ArrayList<>(List.of("A@example.test", " a@example.test ", "b@example.test"));
        JsonNode result = author.session().post("/courses/" + courseId + "/invitations/bulk", Map.of("emails", emails)).expect(201).json();
        assertThat(result.path("invited").asInt()).isEqualTo(2);
        assertThat(result.path("invitations").findValuesAsString("email")).containsExactly("a@example.test", "b@example.test");

        ApiResponse invalid = author.session().post("/courses/" + courseId + "/invitations/bulk", Map.of("emails", List.of("ok@example.test", "nope")));
        assertThat(invalid.json().path("fieldErrors").has("emails[1]")).isTrue();
        assertThat(jdbc.sql("select count(*) from course_invitations where recipient_email = 'ok@example.test'").query(Long.class).single())
                .as("all or nothing").isZero();

        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add("user" + i + "@example.test");
        }
        assertThat(author.session().post("/courses/" + courseId + "/invitations/bulk", Map.of("emails", tooMany)).json().path("fieldErrors").has("emails"))
                .isTrue();
        runJobs();
        assertThat(email.sent().stream().filter(message -> message.subject().startsWith("Invitation to"))).hasSize(2);
    }

    @Test
    void onlyApprovedAuthorsInviteAndNeverThemselves() {
        TestUser other = approvedTeacher("other", owner);
        assertThat(other.session().post("/courses/" + courseId + "/invitations", Map.of("email", "x@example.test")).status()).isEqualTo(404);
        assertThat(invite("author@example.test").code()).isEqualTo("OWN_COURSE");
        assertThat(other.session().get("/courses/" + courseId + "/invitations").status()).isEqualTo(404);
        assertThat(author.session().get("/courses/" + courseId + "/invitations").status()).isEqualTo(200);
    }

    @Test
    void smtpRetryDeliversAWorkingLinkAndRollbackLeavesNothing() {
        TestUser learner = registerStudent("smtp");
        email.failing(true);
        invite("smtp@example.test").expect(201);
        runJobs();
        email.failing(false);
        clock.advance(Duration.ofMinutes(1));
        learner.session().post("/course-invitations/accept", Map.of("token", tokenFor("smtp@example.test"))).expect(200);
    }

    private ApiResponse invite(String address) {
        return author.session().post("/courses/" + courseId + "/invitations", Map.of("email", address));
    }
}
