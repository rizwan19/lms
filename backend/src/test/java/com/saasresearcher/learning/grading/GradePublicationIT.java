package com.saasresearcher.learning.grading;

import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.written;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ExamClassFixture;
import com.saasresearcher.learning.support.ExamClassFixture.QuestionKey;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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

class GradePublicationIT extends PostgresIntegrationTest {

    private ApiClient admin;
    private TestUser teacher;
    private TestUser student;

    @BeforeEach
    void users() {
        admin = setupAdministrator();
        teacher = createUser(admin, "grader", "grader@example.test", "TEACHER");
        student = createUser(admin, "graded.student", null, "STUDENT");
    }

    @Test
    void writtenAnswersMustBeGradedExplicitlyIncludingZero() {
        Graded attempt = mixedAttempt(Map.of());
        assertThat(finalizeGrade(attempt).code()).isEqualTo("GRADING_INCOMPLETE");

        grade(attempt, "0", "No answer given").expect(200);
        assertThat(finalizeGrade(attempt).expect(200).json().path("earned").decimalValue()).isEqualByComparingTo("1");
    }

    @Test
    void pointsAreBoundedToTheQuestionWithTwoDecimals() {
        Graded attempt = mixedAttempt(Map.of());
        assertThat(grade(attempt, "5.01", "").json().path("fieldErrors").has("points")).isTrue();
        assertThat(grade(attempt, "-1", "").status()).isEqualTo(400);
        assertThat(grade(attempt, "2.345", "").status()).isEqualTo(400);
        assertThat(grade(attempt, "4.5", "x".repeat(5001)).json().path("fieldErrors").has("feedback")).isTrue();
        assertThat(grade(attempt, "4.5", "Good").json().path("points").decimalValue()).isEqualByComparingTo("4.50");
    }

    @Test
    void onlyTheClassTeacherGrades() {
        Graded attempt = mixedAttempt(Map.of());
        TestUser other = createUser(admin, "other.grader", "other@example.test", "TEACHER");
        assertThat(other.session().get("/grading/attempts/" + attempt.id()).status()).isEqualTo(404);
        assertThat(other.session().get("/grading?classId=" + attempt.fixture().classId()).status()).isEqualTo(404);
        assertThat(student.session().get("/grading/attempts/" + attempt.id()).status()).isIn(403, 404);
        assertThat(teacher.session().get("/grading?classId=" + attempt.fixture().classId()).json().path("items").get(0).path("gradeStatus").asString())
                .isEqualTo("DRAFT");
    }

    @Test
    void concurrentGradeSavesWithTheSameRevisionConflict() throws Exception {
        Graded attempt = mixedAttempt(Map.of());
        long revision = revision(attempt);
        ApiClient secondTab = login("grader", PASSWORD);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = List.of(teacher.session(), secondTab).stream().map(tab -> pool.submit(() -> {
                barrier.await();
                return tab.put("/grading/attempts/" + attempt.id() + "/answers/" + attempt.essay(), Map.of("expectedRevision", revision, "points", "3", "feedback", "")).status();
            })).toList();
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test
    void objectiveOnlyExamsPublishAutomatically() {
        ExamClassFixture fixture = ExamClassFixture.create(teacher.session(), student.username(), List.of(singleChoice("Pick A", "2", 0)));
        UUID assignment = fixture.assign(teacher.session(), Map.of());
        String attemptId = student.session().post("/exam-assignments/" + assignment + "/attempts", null).json().path("id").asString();
        QuestionKey key = fixture.key("Pick A");
        student.session().put("/attempts/" + attemptId + "/answers/" + key.questionId(),
                Map.of("expectedRevision", 0, "selectedOptionIds", List.of(key.optionIds().get(0)))).expect(200);
        student.session().post("/attempts/" + attemptId + "/submit", null).expect(200);

        JsonNode result = student.session().get("/me/results").json().get(0);
        assertThat(result.path("state").asString()).isEqualTo("PUBLISHED");
        assertThat(result.path("earned").decimalValue()).isEqualByComparingTo("2");
        assertThat(result.path("passed").asBoolean()).isTrue();
    }

    @Test
    void mixedResultsStayPendingUntilTeacherPublication() {
        Graded attempt = mixedAttempt(Map.of());
        JsonNode pending = student.session().get("/me/results").json().get(0);
        assertThat(pending.path("state").asString()).isEqualTo("PENDING");
        assertThat(pending.path("earned").isNull()).isTrue();
        assertThat(student.session().get("/me/results/" + attempt.id()).status()).isEqualTo(404);

        grade(attempt, "4", "Clear explanation").expect(200);
        assertThat(publish(attempt).code()).isEqualTo("NOT_FINALIZED");
        finalizeGrade(attempt).expect(200);
        assertThat(student.session().get("/me/results").json().get(0).path("state").asString()).isEqualTo("PENDING");
        publish(attempt).expect(200);

        JsonNode detail = student.session().get("/me/results/" + attempt.id()).expect(200).json();
        assertThat(detail.path("earned").decimalValue()).isEqualByComparingTo("5");
        assertThat(detail.path("answersReleased").asBoolean()).isFalse();
        assertThat(detail.path("questions").findValuesAsString("feedback")).contains("Clear explanation");
        assertThat(detail.toString()).doesNotContain("Because.", "gradingNotes");
        assertThat(detail.path("questions").get(0).path("correctAnswers").isNull()).isTrue();
    }

    @Test
    void keysAreReleasedOnlyAfterClosingWhenConfigured() {
        Instant closes = clock.instant().plus(Duration.ofHours(2));
        Graded attempt = mixedAttempt(Map.of("closesAt", closes.toString(), "feedbackPolicy", "AFTER_CLOSE"));
        grade(attempt, "4", "").expect(200);
        finalizeGrade(attempt).expect(200);
        publish(attempt).expect(200);

        assertThat(student.session().get("/me/results/" + attempt.id()).json().path("answersReleased").asBoolean()).isFalse();
        clock.set(closes);
        JsonNode released = student.session().get("/me/results/" + attempt.id()).json();
        assertThat(released.path("answersReleased").asBoolean()).isTrue();
        assertThat(released.toString()).contains("Because.", "Option A");
    }

    @Test
    void regradeKeepsThePreviousPublishedScoreUntilReplaced() {
        Graded attempt = mixedAttempt(Map.of());
        grade(attempt, "2", "").expect(200);
        finalizeGrade(attempt).expect(200);
        publish(attempt).expect(200);
        assertThat(publishedEarned()).isEqualByComparingTo("3");

        assertThat(teacher.session().post("/grading/attempts/" + attempt.id() + "/regrade", Map.of("reason", " ")).status()).isEqualTo(400);
        teacher.session().post("/grading/attempts/" + attempt.id() + "/regrade", Map.of("reason", "Rubric applied too strictly")).expect(200);
        assertThat(teacher.session().post("/grading/attempts/" + attempt.id() + "/regrade", Map.of("reason", "Again")).code()).isEqualTo("REGRADE_OPEN");
        grade(attempt, "5", "Full marks on review").expect(200);
        assertThat(publishedEarned()).as("draft regrade is invisible").isEqualByComparingTo("3");
        finalizeGrade(attempt).expect(200);
        assertThat(publishedEarned()).isEqualByComparingTo("3");
        publish(attempt).expect(200);

        assertThat(publishedEarned()).isEqualByComparingTo("6");
        assertThat(jdbc.sql("select count(*) from grade_revisions where status = 'SUPERSEDED'").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from audit_events where action = 'grade.published' and metadata->>'previous' = '3.00'")
                .query(Long.class).single()).isEqualTo(1);
    }

    private record Graded(ExamClassFixture fixture, String id, UUID essay) {
    }

    /** A submitted mixed attempt: the single-choice answer is correct (1 point), the essay (5 points) is ungraded. */
    private Graded mixedAttempt(Map<String, Object> schedule) {
        ExamClassFixture fixture = ExamClassFixture.create(teacher.session(), student.username(),
                List.of(singleChoice("Pick A", "1", 0), written("Essay", "5")));
        UUID assignment = fixture.assign(teacher.session(), new HashMap<>(schedule));
        String attemptId = student.session().post("/exam-assignments/" + assignment + "/attempts", null).expect(200).json().path("id").asString();
        QuestionKey single = fixture.key("Pick A");
        QuestionKey essay = fixture.key("Essay");
        student.session().put("/attempts/" + attemptId + "/answers/" + single.questionId(),
                Map.of("expectedRevision", 0, "selectedOptionIds", List.of(single.optionIds().get(0)))).expect(200);
        student.session().put("/attempts/" + attemptId + "/answers/" + essay.questionId(), Map.of("expectedRevision", 0, "writtenText", "Osmosis is...")).expect(200);
        student.session().post("/attempts/" + attemptId + "/submit", null).expect(200);
        return new Graded(fixture, attemptId, essay.questionId());
    }

    private long revision(Graded attempt) {
        return teacher.session().get("/grading/attempts/" + attempt.id()).expect(200).json().path("revision").path("version").asLong();
    }

    private ApiResponse grade(Graded attempt, String points, String feedback) {
        return teacher.session().put("/grading/attempts/" + attempt.id() + "/answers/" + attempt.essay(),
                Map.of("expectedRevision", revision(attempt), "points", points, "feedback", feedback));
    }

    private ApiResponse finalizeGrade(Graded attempt) {
        return teacher.session().post("/grading/attempts/" + attempt.id() + "/finalize", Map.of("expectedRevision", revision(attempt)));
    }

    private ApiResponse publish(Graded attempt) {
        return teacher.session().post("/grading/attempts/" + attempt.id() + "/publish", Map.of("expectedRevision", revision(attempt)));
    }

    private java.math.BigDecimal publishedEarned() {
        JsonNode result = student.session().get("/me/results").json().get(0);
        return result.path("earned").decimalValue();
    }
}
