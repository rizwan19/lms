package com.saasresearcher.learning.grading;

import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.written;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.grading.api.RevisionStatus;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.CourseExamFixture.QuestionKey;
import com.saasresearcher.learning.support.CourseExamFixture;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.math.BigDecimal;
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

/** Only the course author grades; learners read only published revisions. */
class CourseAuthorGradingIT extends PostgresIntegrationTest {

    private TestUser owner;
    private TestUser author;
    private TestUser learner;

    @BeforeEach
    void users() {
        owner = setupOwner();
        author = approvedTeacher("grader", owner);
        learner = registerStudent("graded.learner");
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
    void onlyTheCourseAuthorGradesDespiteWorkspaceApproval() {
        Graded attempt = mixedAttempt(Map.of());
        TestUser other = approvedTeacher("other.grader", owner);
        for (ApiClient outsider : List.of(other.session(), owner.session(), learner.session())) {
            assertThat(outsider.get("/grading/attempts/" + attempt.id()).status()).isEqualTo(404);
            assertThat(outsider.get("/courses/" + attempt.fixture().courseId() + "/grading").status()).isIn(403, 404);
        }
        JsonNode queue = author.session().get("/courses/" + attempt.fixture().courseId() + "/grading").expect(200).json();
        assertThat(queue.path("items").get(0).path("gradeStatus").asString()).isEqualTo("DRAFT");
        assertThat(queue.path("items").get(0).path("username").asString()).isEqualTo("graded.learner");
        assertThat(author.session().get("/courses/" + attempt.fixture().courseId() + "/grading?status=PUBLISHED").json().path("total").asLong()).isZero();
        assertThat(author.session().get("/courses/" + attempt.fixture().courseId() + "/grading?versionId=" + attempt.fixture().versionId())
                .json().path("total").asLong()).isEqualTo(1);
    }

    @Test
    void concurrentGradeSavesWithTheSameRevisionConflict() throws Exception {
        Graded attempt = mixedAttempt(Map.of());
        long before = revision(attempt);
        grade(attempt, "1", "").expect(200);
        assertThat(revision(attempt)).as("every grade save bumps the revision").isGreaterThan(before);
        long revision = revision(attempt);
        ApiClient secondTab = login("grader", PASSWORD);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = List.of(author.session(), secondTab).stream().map(tab -> pool.submit(() -> {
                barrier.await();
                return tab.put("/grading/attempts/" + attempt.id() + "/answers/" + attempt.essay(),
                        Map.of("expectedRevision", revision, "points", "3", "feedback", "")).status();
            })).toList();
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test
    void objectiveOnlyExamsPublishAutomatically() {
        CourseExamFixture fixture = CourseExamFixture.create(author.session(), learner.session(), List.of(singleChoice("Pick A", "2", 0)), true, Map.of());
        String attemptId = fixture.start(learner.session()).json().path("id").asString();
        QuestionKey key = fixture.key("Pick A");
        learner.session().put("/attempts/" + attemptId + "/answers/" + key.questionId(),
                Map.of("selectedOptionIds", List.of(key.optionIds().get(0)))).expect(200);
        learner.session().post("/attempts/" + attemptId + "/submit", null).expect(200);

        JsonNode result = learner.session().get("/me/results").json().get(0);
        assertThat(result.path("state").asString()).isEqualTo("PUBLISHED");
        assertThat(result.path("earned").decimalValue()).isEqualByComparingTo("2");
        assertThat(result.path("passed").asBoolean()).isTrue();
        assertThat(result.path("courseId").asString()).isEqualTo(fixture.courseId().toString());
    }

    @Test
    void mixedResultsStayPendingUntilTheAuthorPublishes() {
        Graded attempt = mixedAttempt(Map.of());
        JsonNode pending = learner.session().get("/me/results").json().get(0);
        assertThat(pending.path("state").asString()).isEqualTo("PENDING");
        assertThat(pending.path("earned").isNull()).isTrue();
        assertThat(learner.session().get("/me/results/" + attempt.id()).status()).isEqualTo(404);

        grade(attempt, "4", "Clear explanation").expect(200);
        assertThat(publish(attempt).code()).isEqualTo("NOT_FINALIZED");
        finalizeGrade(attempt).expect(200);
        assertThat(learner.session().get("/me/results").json().get(0).path("state").asString()).isEqualTo("PENDING");
        publish(attempt).expect(200);

        JsonNode detail = learner.session().get("/me/results/" + attempt.id()).expect(200).json();
        assertThat(detail.path("earned").decimalValue()).isEqualByComparingTo("5");
        assertThat(detail.path("answersReleased").asBoolean()).isFalse();
        assertThat(detail.path("questions").findValuesAsString("feedback")).contains("Clear explanation");
        assertThat(detail.toString()).doesNotContain("Because.", "gradingNotes");
        assertThat(detail.path("questions").get(0).path("correctAnswers").isNull()).isTrue();
    }

    @Test
    void keysAreReleasedOnlyAfterClosingWhenConfigured() {
        Instant closes = clock.instant().plus(Duration.ofHours(2));
        Graded attempt = mixedAttempt(Map.of("mode", "SCHEDULED", "opensAt", clock.instant().toString(), "closesAt", closes.toString(),
                "feedbackPolicy", "AFTER_CLOSE"));
        grade(attempt, "4", "").expect(200);
        finalizeGrade(attempt).expect(200);
        publish(attempt).expect(200);

        assertThat(learner.session().get("/me/results/" + attempt.id()).json().path("answersReleased").asBoolean()).isFalse();
        clock.set(closes);
        JsonNode released = learner.session().get("/me/results/" + attempt.id()).json();
        assertThat(released.path("answersReleased").asBoolean()).isTrue();
        assertThat(released.toString()).contains("Because.", "Option A");
    }

    @Test
    void regradeDraftsStayPrivateUntilPublished() {
        Graded attempt = mixedAttempt(Map.of());
        grade(attempt, "2", "").expect(200);
        finalizeGrade(attempt).expect(200);
        publish(attempt).expect(200);
        assertThat(publishedEarned()).isEqualByComparingTo("3");

        assertThat(author.session().post("/grading/attempts/" + attempt.id() + "/regrade", Map.of("reason", " ")).status()).isEqualTo(400);
        author.session().post("/grading/attempts/" + attempt.id() + "/regrade", Map.of("reason", "Rubric applied too strictly")).expect(200);
        assertThat(author.session().post("/grading/attempts/" + attempt.id() + "/regrade", Map.of("reason", "Again")).code()).isEqualTo("REGRADE_OPEN");
        grade(attempt, "5", "Full marks on review").expect(200);
        assertThat(publishedEarned()).as("draft regrade is invisible").isEqualByComparingTo("3");
        finalizeGrade(attempt).expect(200);
        assertThat(publishedEarned()).isEqualByComparingTo("3");
        publish(attempt).expect(200);

        assertThat(publishedEarned()).isEqualByComparingTo("6");
        assertThat(jdbc.sql("select count(*) from grade_revisions where status = :status").param("status", RevisionStatus.SUPERSEDED.code()).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from audit_events where action = 'grade.published' and metadata->>'previous' = '3.00'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void teachersLearningElsewhereSeeOnlyTheirOwnPublishedResults() {
        TestUser teacherLearner = approvedTeacher("teacher.learner", owner);
        CourseExamFixture fixture = CourseExamFixture.create(author.session(), teacherLearner.session(), List.of(singleChoice("Pick A", "1", 0)),
                true, Map.of());
        String attemptId = fixture.start(teacherLearner.session()).json().path("id").asString();
        teacherLearner.session().post("/attempts/" + attemptId + "/submit", null).expect(200);
        assertThat(teacherLearner.session().get("/me/results/" + attemptId).status()).isEqualTo(200);
        assertThat(teacherLearner.session().get("/grading/attempts/" + attemptId).status()).as("learning grants no grading").isEqualTo(404);
        assertThat(learner.session().get("/me/results/" + attemptId).status()).isEqualTo(404);
    }

    @Test
    void publicationRechecksTheAuthorsApproval() {
        Graded attempt = mixedAttempt(Map.of());
        grade(attempt, "4", "").expect(200);
        finalizeGrade(attempt).expect(200);
        long revision = owner.session().get("/workspace/users/" + author.id()).json().path("revision").asLong();
        owner.session().put("/workspace/users/" + author.id() + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision)).expect(200);
        assertThat(author.session().post("/grading/attempts/" + attempt.id() + "/publish", Map.of("expectedRevision", 0)).status()).isEqualTo(401);
        assertThat(learner.session().get("/me/results").json().get(0).path("state").asString()).isEqualTo("PENDING");
    }

    private record Graded(CourseExamFixture fixture, String id, UUID essay) {
    }

    /** A submitted mixed attempt: the single-choice answer is correct (1 point), the essay (5 points) is ungraded. */
    private Graded mixedAttempt(Map<String, Object> schedule) {
        CourseExamFixture fixture = CourseExamFixture.create(author.session(), learner.session(),
                List.of(singleChoice("Pick A", "1", 0), written("Essay", "5")), true, new HashMap<>(schedule));
        String attemptId = fixture.start(learner.session()).expect(200).json().path("id").asString();
        QuestionKey single = fixture.key("Pick A");
        QuestionKey essay = fixture.key("Essay");
        learner.session().put("/attempts/" + attemptId + "/answers/" + single.questionId(),
                Map.of("selectedOptionIds", List.of(single.optionIds().get(0)))).expect(200);
        learner.session().put("/attempts/" + attemptId + "/answers/" + essay.questionId(), Map.of("writtenText", "Osmosis is..."))
                .expect(200);
        learner.session().post("/attempts/" + attemptId + "/submit", null).expect(200);
        return new Graded(fixture, attemptId, essay.questionId());
    }

    private long revision(Graded attempt) {
        return author.session().get("/grading/attempts/" + attempt.id()).expect(200).json().path("revision").path("version").asLong();
    }

    private ApiResponse grade(Graded attempt, String points, String feedback) {
        return author.session().put("/grading/attempts/" + attempt.id() + "/answers/" + attempt.essay(),
                Map.of("expectedRevision", revision(attempt), "points", points, "feedback", feedback));
    }

    private ApiResponse finalizeGrade(Graded attempt) {
        return author.session().post("/grading/attempts/" + attempt.id() + "/finalize", Map.of("expectedRevision", revision(attempt)));
    }

    private ApiResponse publish(Graded attempt) {
        return author.session().post("/grading/attempts/" + attempt.id() + "/publish", Map.of("expectedRevision", revision(attempt)));
    }

    private BigDecimal publishedEarned() {
        return learner.session().get("/me/results").json().get(0).path("earned").decimalValue();
    }
}
