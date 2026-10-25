package com.saasresearcher.learning.enrollment;

import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static com.saasresearcher.learning.support.CourseFixtures.saveDraft;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.ExamFixtures.createExam;
import static com.saasresearcher.learning.support.ExamFixtures.saveExam;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.enrollment.api.GrantSource;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.EnrollmentFixtures;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
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

/** Requesting is never access: only the course author's approval grants an enrollment. */
class CourseAccessRequestIT extends PostgresIntegrationTest {

    private TestUser owner;
    private TestUser author;
    private TestUser learner;
    private UUID courseId;

    @BeforeEach
    void course() {
        owner = setupOwner();
        author = approvedTeacher("author", owner);
        learner = registerStudent("learner");
        courseId = UUID.fromString(publishedCourse(author.session(), "Chemistry", List.of(lesson("Atoms", "<p>x</p>", true))).path("courseId").asString());
    }

    @Test
    void requestStaysPendingUntilTheAuthorApproves() {
        JsonNode request = request(learner.session()).expect(200).json();
        assertThat(request.path("status").asString()).isEqualTo("PENDING");
        assertThat(request(learner.session()).json().path("id").asString()).as("repeat returns the pending request").isEqualTo(request.path("id").asString());
        assertThat(learner.session().get("/me/enrollments").json()).as("requesting is not access").isEmpty();
        runJobs();
        assertThat(author.session().get("/notifications").json().path("items").findValuesAsString("type")).contains("access-request.created");

        JsonNode inbox = author.session().get("/courses/" + courseId + "/access-requests").expect(200).json();
        assertThat(inbox.path("items").get(0).path("learnerUsername").asString()).isEqualTo("learner");
        decide(author.session(), request, true).expect(200);

        UUID enrollmentId = EnrollmentFixtures.enrollmentOf(learner.session(), courseId);
        JsonNode content = learner.session().get("/me/enrollments/" + enrollmentId + "/content").expect(200).json();
        assertThat(content.path("course").path("edition").asInt()).isEqualTo(1);
        assertThat(learner.session().get("/me/access-requests").json().get(0).path("status").asString()).isEqualTo("APPROVED");
        assertThat(learner.session().get("/notifications").json().path("items").findValuesAsString("type")).contains("access-request.decided");
        assertThat(GrantSource.fromCode(jdbc.sql("select grant_source from course_enrollments").query(Short.class).single())).isEqualTo(GrantSource.REQUEST);
        assertThat(jdbc.sql("select count(*) from course_access_events").query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void onlyTheAuthorDecidesAndAuthorsCannotEnrollInTheirOwnCourse() {
        JsonNode request = request(learner.session()).json();
        TestUser otherTeacher = approvedTeacher("other", owner);
        assertThat(decide(otherTeacher.session(), request, true).status()).isEqualTo(404);
        assertThat(decide(owner.session(), request, true).status()).as("workspace powers grant nothing here").isEqualTo(404);
        assertThat(decide(learner.session(), request, true).status()).isEqualTo(404);
        assertThat(otherTeacher.session().get("/courses/" + courseId + "/access-requests").status()).isEqualTo(404);
        assertThat(request(author.session()).code()).isEqualTo("OWN_COURSE");
    }

    @Test
    void teachersCanLearnInOtherAuthorsCourses() {
        TestUser teacher = registerTeacher("teacher.learner");
        UUID enrollmentId = EnrollmentFixtures.enroll(author.session(), teacher.session(), courseId);
        assertThat(teacher.session().get("/me/enrollments/" + enrollmentId + "/content").status()).isEqualTo(200);
    }

    @Test
    void rejectionStartsADayLongCooldownWhileCancellationDoesNot() {
        JsonNode request = request(learner.session()).json();
        decide(author.session(), request, false, "Course is full").expect(200);
        ApiResponse cooling = request(learner.session());
        assertThat(cooling.code()).isEqualTo("ACCESS_REQUEST_COOLDOWN");
        assertThat(learner.session().get("/me/access-requests").json().get(0).path("reason").asString()).isEqualTo("Course is full");
        clock.advance(Duration.ofHours(24));
        JsonNode again = request(learner.session()).expect(200).json();
        assertThat(again.path("id").asString()).as("one current row per course and learner").isEqualTo(request.path("id").asString());

        learner.session().post("/access-requests/" + again.path("id").asString() + "/cancel", null).expect(200);
        assertThat(request(learner.session()).json().path("status").asString()).isEqualTo("PENDING");
        assertThat(jdbc.sql("select count(*) from course_access_events").query(Long.class).single()).isEqualTo(5);
    }

    @Test
    void concurrentApprovalsCreateOneEnrollment() throws Exception {
        JsonNode request = request(learner.session()).json();
        ApiClient secondTab = login("author", PASSWORD);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (ApiClient tab : List.of(author.session(), secondTab)) {
                results.add(pool.submit(() -> {
                    barrier.await();
                    return decide(tab, request, true).status();
                }));
            }
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsOnly(200);
        }
        assertThat(jdbc.sql("select count(*) from course_enrollments").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void suspensionAndRestorationKeepTheSameEnrollmentAndProgress() {
        UUID enrollmentId = EnrollmentFixtures.enroll(author.session(), learner.session(), courseId);
        String lessonId = learner.session().get("/me/enrollments/" + enrollmentId + "/content").json().path("course").path("sections").get(0)
                .path("lessons").get(0).path("id").asString();
        learner.session().put("/enrollments/" + enrollmentId + "/lessons/" + lessonId + "/progress",
                Map.of("expectedRevision", 0, "completed", true, "resumeSeconds", 0)).expect(200);

        changeStatus(enrollmentId, "SUSPENDED").expect(200);
        ApiResponse blocked = learner.session().get("/me/enrollments/" + enrollmentId + "/content");
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.code()).isEqualTo("ENROLLMENT_INACTIVE");
        assertThat(learner.session().get("/me/learning").json().get(0).path("summary").path("completed").asInt()).as("history stays readable").isEqualTo(1);
        assertThat(changeStatus(enrollmentId, "WITHDRAWN").code()).isEqualTo("INVALID_STATUS_CHANGE");

        changeStatus(enrollmentId, "ACTIVE").expect(200);
        assertThat(learner.session().get("/me/enrollments/" + enrollmentId + "/content").json().path("progress").get(0).path("completed").asBoolean())
                .isTrue();

        learner.session().post("/me/enrollments/" + enrollmentId + "/withdraw", null).expect(204);
        JsonNode again = request(learner.session()).expect(200).json();
        decide(author.session(), again, true).expect(200);
        assertThat(EnrollmentFixtures.enrollmentOf(learner.session(), courseId)).as("restored on the original row").isEqualTo(enrollmentId);
        assertThat(jdbc.sql("select count(*) from course_enrollments").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void newEnrollmentsPinTheLatestEditionAndExistingOnesKeepTheirs() {
        UUID first = EnrollmentFixtures.enroll(author.session(), learner.session(), courseId);
        String firstVersion = author.session().get("/courses/" + courseId).json().path("editions").get(0).path("versionId").asString();
        author.session().post("/courses/" + courseId + "/draft", Map.of("fromVersionId", firstVersion)).expect(201);
        saveDraft(author.session(), courseId, "Chemistry", List.of(section("S", List.of(lesson("Atoms v2", "", true)))));
        publish(author.session(), courseId);

        TestUser later = registerStudent("later");
        UUID second = EnrollmentFixtures.enroll(author.session(), later.session(), courseId);
        assertThat(learner.session().get("/me/enrollments/" + first + "/content").json().path("course").path("edition").asInt()).isEqualTo(1);
        assertThat(later.session().get("/me/enrollments/" + second + "/content").json().path("course").path("edition").asInt()).isEqualTo(2);
    }

    @Test
    void closedRequiredScheduledExamsBlockNewGrants() {
        UUID scheduled = createCourse(author.session(), "Scheduled");
        saveDraft(author.session(), scheduled, "Scheduled", List.of(section("S", List.of(lesson("L", "", true)))));
        Instant opens = clock.instant().plus(Duration.ofHours(1));
        UUID examId = createExam(author.session(), scheduled, "Final", true,
                Map.of("mode", "SCHEDULED", "opensAt", opens.toString(), "closesAt", opens.plus(Duration.ofHours(2)).toString()));
        saveExam(author.session(), examId, "Final", "50", List.of(singleChoice("Q", "1", 0))).expect(200);
        publish(author.session(), scheduled);
        TestUser early = registerStudent("early");
        EnrollmentFixtures.enroll(author.session(), early.session(), scheduled);

        clock.set(opens.plus(Duration.ofHours(2)));
        JsonNode request = learner.session().post("/courses/" + scheduled + "/access-requests", null).expect(200).json();
        ApiResponse blocked = decide(author.session(), request, true);
        assertThat(blocked.status()).isEqualTo(409);
        assertThat(blocked.code()).isEqualTo("REQUIRED_EXAM_CLOSED");

        long revision = author.session().get("/exams/" + examId).json().path("revision").asLong();
        author.session().put("/exams/" + examId + "/schedule", Map.of("expectedRevision", revision,
                "mode", "SCHEDULED", "opensAt", opens.toString(), "closesAt", opens.plus(Duration.ofDays(7)).toString())).expect(200);
        decide(author.session(), request, true).expect(200);
    }

    @Test
    void archivedCoursesAcceptNoRequests() {
        author.session().post("/courses/" + courseId + "/archive", null).expect(204);
        assertThat(request(learner.session()).status()).isEqualTo(404);
    }

    private ApiResponse request(ApiClient session) {
        return session.post("/courses/" + courseId + "/access-requests", null);
    }

    private static ApiResponse decide(ApiClient session, JsonNode request, boolean approve) {
        return decide(session, request, approve, null);
    }

    private static ApiResponse decide(ApiClient session, JsonNode request, boolean approve, String reason) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("expectedRevision", request.path("revision").asLong(), "approve", approve));
        body.put("reason", reason);
        return session.post("/access-requests/" + request.path("id").asString() + "/decision", body);
    }

    private ApiResponse changeStatus(UUID enrollmentId, String status) {
        long revision = author.session().get("/courses/" + courseId + "/enrollments").json().path("items").valueStream()
                .filter(row -> row.path("enrollmentId").asString().equals(enrollmentId.toString()))
                .findFirst().orElseThrow().path("revision").asLong();
        return author.session().put("/enrollments/" + enrollmentId + "/status", Map.of("status", status, "expectedRevision", revision));
    }
}
