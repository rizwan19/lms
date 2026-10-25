package com.saasresearcher.learning.assessments;

import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.ExamFixtures.multipleSelect;
import static com.saasresearcher.learning.support.ExamFixtures.saveExam;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.trueFalse;
import static com.saasresearcher.learning.support.ExamFixtures.written;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.service.ExpiredAttemptSweep;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.CourseExamFixture;
import com.saasresearcher.learning.support.CourseExamFixture.QuestionKey;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Attempts on course exams: one active attempt, server deadlines fixed at start, acknowledged saves and expiry recovery. */
class CourseAttemptConcurrencyIT extends PostgresIntegrationTest {

    @Autowired
    private ExpiredAttemptSweep sweep;

    private TestUser owner;
    private TestUser author;
    private TestUser learner;

    @BeforeEach
    void users() {
        owner = setupOwner();
        author = approvedTeacher("author", owner);
        learner = registerStudent("learner");
        clock.set(Instant.parse("2030-01-01T10:00:00Z"));
    }

    @Test
    void simultaneousStartsCreateOneAttempt() throws Exception {
        CourseExamFixture fixture = fixture(Map.of());
        ApiClient secondTab = login(learner.username(), PASSWORD);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<ApiResponse>> responses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (ApiClient tab : List.of(learner.session(), secondTab)) {
                responses.add(pool.submit(() -> {
                    barrier.await();
                    return fixture.start(tab);
                }));
            }
            List<String> ids = new ArrayList<>();
            for (Future<ApiResponse> response : responses) {
                if (response.get().status() == 200) {
                    ids.add(response.get().json().path("id").asString());
                } else {
                    assertThat(response.get().code()).isEqualTo("DUPLICATE");
                }
            }
            assertThat(ids).isNotEmpty().allMatch(ids.getFirst()::equals);
        }
        assertThat(jdbc.sql("select count(*) from exam_attempts").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select course_version_id from exam_attempts").query(UUID.class).single()).isEqualTo(fixture.versionId());
    }

    @Test
    void selfPacedDeadlineIsTheDurationAndScheduledDeadlinesStopAtClosing() {
        JsonNode selfPaced = fixture(Map.of("durationMinutes", 45)).start(learner.session()).expect(200).json();
        assertThat(Instant.parse(selfPaced.path("expiresAt").asString())).isEqualTo(clock.instant().plus(Duration.ofMinutes(45)));
        assertThat(Instant.parse(selfPaced.path("serverNow").asString())).isEqualTo(clock.instant());

        Instant closes = clock.instant().plus(Duration.ofMinutes(30));
        JsonNode truncated = fixture(Map.of("mode", "SCHEDULED", "opensAt", clock.instant().toString(), "closesAt", closes.toString(),
                "durationMinutes", 60)).start(learner.session()).expect(200).json();
        assertThat(Instant.parse(truncated.path("expiresAt").asString())).isEqualTo(closes);
    }

    @Test
    void scheduleEditsNeverMoveAStartedDeadline() {
        CourseExamFixture fixture = fixture(Map.of("durationMinutes", 30));
        JsonNode attempt = fixture.start(learner.session()).expect(200).json();
        JsonNode exam = author.session().get("/courses/" + fixture.courseId() + "/exams").json().get(0);
        author.session().put("/exams/" + fixture.examId() + "/schedule", Map.of("expectedRevision", exam.path("revision").asLong(),
                "durationMinutes", 5)).expect(200);
        clock.advance(Duration.ofMinutes(10));
        JsonNode reconnect = learner.session().get("/attempts/" + attempt.path("id").asString()).expect(200).json();
        assertThat(reconnect.path("expiresAt").asString()).isEqualTo(attempt.path("expiresAt").asString());
        assertThat(reconnect.path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(fixture.start(learner.session()).json().path("id").asString()).isEqualTo(attempt.path("id").asString());
    }

    @Test
    void attemptLimitIsEnforced() {
        CourseExamFixture fixture = fixture(Map.of("maxAttempts", 2));
        String first = fixture.start(learner.session()).json().path("id").asString();
        learner.session().post("/attempts/" + first + "/submit", null).expect(200);
        String second = fixture.start(learner.session()).expect(200).json().path("id").asString();
        assertThat(second).isNotEqualTo(first);
        learner.session().post("/attempts/" + second + "/submit", null).expect(200);
        assertThat(fixture.start(learner.session()).code()).isEqualTo("ATTEMPT_LIMIT_REACHED");
    }

    @Test
    void snapshotOrderIsStableAndLaterEditionsCannotChangeIt() {
        CourseExamFixture fixture = fixture(Map.of());
        JsonNode attempt = fixture.start(learner.session()).json();
        author.session().post("/courses/" + fixture.courseId() + "/draft", Map.of("fromVersionId", fixture.versionId().toString())).expect(201);
        UUID copy = UUID.fromString(author.session().get("/courses/" + fixture.courseId() + "/exams").json().get(0).path("id").asString());
        saveExam(author.session(), copy, "Quiz", "50", List.of(singleChoice("Changed prompt", "1", 0))).expect(200);
        publish(author.session(), fixture.courseId());

        JsonNode again = learner.session().get("/attempts/" + attempt.path("id").asString()).expect(200).json();
        assertThat(again.path("questions")).isEqualTo(attempt.path("questions"));
        assertThat(again.path("questions").findValuesAsString("prompt")).doesNotContain("Changed prompt").hasSize(4);
    }

    @Test
    void learnerPayloadsNeverContainKeysOrGradingNotes() {
        CourseExamFixture fixture = fixture(Map.of());
        String body = fixture.start(learner.session()).text();
        assertThat(body).doesNotContain("\"correct\"", "gradingNotes", "explanation", "Because.", "autoPoints", "objectiveScore");
        String list = learner.session().get("/me/enrollments/" + fixture.enrollmentId() + "/exams").expect(200).text();
        assertThat(list).doesNotContain("\"correct\"", "objectiveScore");
    }

    @Test
    void onlyTheEnrolledLearnerStartsOrReadsTheirAttempt() {
        CourseExamFixture fixture = fixture(Map.of());
        TestUser outsider = registerStudent("outsider");
        assertThat(fixture.start(outsider.session()).status()).isEqualTo(404);
        String attemptId = fixture.start(learner.session()).json().path("id").asString();
        assertThat(outsider.session().get("/attempts/" + attemptId).status()).isEqualTo(404);
        assertThat(author.session().get("/attempts/" + attemptId).status()).isEqualTo(404);
        assertThat(owner.session().get("/attempts/" + attemptId).status()).isEqualTo(404);
        assertThat(owner.session().get("/grading/attempts/" + attemptId).status()).as("no workspace bypass into private attempts").isEqualTo(404);

        learner.session().post("/attempts/" + attemptId + "/submit", null).expect(200);
        long revision = author.session().get("/courses/" + fixture.courseId() + "/enrollments").json().path("items").get(0).path("revision").asLong();
        author.session().put("/enrollments/" + fixture.enrollmentId() + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision)).expect(200);
        assertThat(fixture.start(learner.session()).code()).isEqualTo("ENROLLMENT_INACTIVE");
    }

    @Test
    void answersMustMatchTheQuestionType() {
        Started started = started();
        QuestionKey single = started.fixture().key("Pick A");
        assertThat(save(started, single.questionId(), single.optionIds(), null).status()).isEqualTo(400);
        assertThat(save(started, single.questionId(), List.of(UUID.randomUUID().toString()), null).status()).isEqualTo(400);
        UUID essay = started.fixture().key("Essay").questionId();
        assertThat(save(started, essay, null, "x".repeat(20_001)).status()).isEqualTo(400);
        assertThat(save(started, essay, null, "x".repeat(20_000)).status()).isEqualTo(200);
    }

    @Test
    void submitIsIdempotentAndScoresExactSetsOnly() {
        Started started = started();
        QuestionKey single = started.fixture().key("Pick A");
        QuestionKey multiple = started.fixture().key("Pick two");
        save(started, single.questionId(), List.of(single.optionIds().get(0)), null).expect(200);
        save(started, multiple.questionId(), multiple.optionIds(), null).expect(200);

        JsonNode first = submit(started).expect(200).json();
        JsonNode again = submit(started).expect(200).json();
        assertThat(again).isEqualTo(first);
        assertThat(first.path("status").asString()).isEqualTo("AWAITING_GRADING");
        assertThat(objectiveScore()).isEqualByComparingTo("1");
        assertThat(save(started, single.questionId(), List.of(single.optionIds().get(1)), null).code()).isEqualTo("ATTEMPT_CLOSED");
        assertThat(jdbc.sql("select count(*) from grade_revisions").query(Long.class).single()).as("finalized exactly once").isEqualTo(1);
    }

    @Test
    void writesAtTheDeadlineAreRejectedAndTheAttemptFinalizesFromSavedAnswers() {
        Started started = started();
        QuestionKey single = started.fixture().key("Pick A");
        save(started, single.questionId(), List.of(single.optionIds().get(0)), null).expect(200);

        clock.set(started.expiresAt().minusMillis(1));
        save(started, started.fixture().key("Essay").questionId(), null, "Just in time").expect(200);
        clock.set(started.expiresAt());
        assertThat(save(started, single.questionId(), List.of(single.optionIds().get(1)), null).code()).isEqualTo("ATTEMPT_EXPIRED");

        JsonNode attempt = learner.session().get("/attempts/" + started.id()).json();
        assertThat(attempt.path("status").asString()).isEqualTo("EXPIRED");
        assertThat(objectiveScore()).isEqualByComparingTo("1");
        assertThat(attempt.path("answers").findValuesAsString("writtenText")).contains("Just in time");
    }

    @Test
    void sweepFinalizesAbandonedAttemptsEvenForSuspendedAccounts() {
        Started started = started();
        QuestionKey single = started.fixture().key("Pick A");
        save(started, single.questionId(), List.of(single.optionIds().get(0)), null).expect(200);
        long revision = owner.session().get("/workspace/users/" + learner.id()).json().path("revision").asLong();
        owner.session().put("/workspace/users/" + learner.id() + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision)).expect(200);
        assertThat(save(started, single.questionId(), List.of(), null).status()).isEqualTo(401);

        assertThat(sweep.sweep()).isZero();
        clock.set(started.expiresAt());
        assertThat(sweep.sweep()).isEqualTo(1);
        assertThat(sweep.sweep()).isZero();
        assertThat(attemptStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(objectiveScore()).isEqualByComparingTo("1");
    }

    @Test
    void racingSaveAndSubmitNeverLeaveAnAnswerAfterFinalization() throws Exception {
        Started started = started();
        UUID essay = started.fixture().key("Essay").questionId();
        for (int round = 0; round < 5; round++) {
            CyclicBarrier barrier = new CyclicBarrier(2);
            boolean submitting = round == 4;
            try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
                Future<Integer> saving = pool.submit(() -> {
                    barrier.await();
                    return save(started, essay, null, "round").status();
                });
                Future<Integer> other = pool.submit(() -> {
                    barrier.await();
                    return submitting ? submit(started).status() : 200;
                });
                assertThat(saving.get()).isIn(200, 409);
                assertThat(other.get()).isEqualTo(200);
            }
        }
        Timestamp finalizedAt = jdbc.sql("select finalized_at from exam_attempts").query(Timestamp.class).single();
        Timestamp lastSave = jdbc.sql("select max(saved_at) from attempt_answers").query(Timestamp.class).single();
        assertThat(lastSave).isBeforeOrEqualTo(finalizedAt);
        assertThat(save(started, essay, null, "late").code()).isEqualTo("ATTEMPT_CLOSED");
    }

    @Test
    void racingSubmitAndExpiryFinalizeOnce() throws Exception {
        Started started = started();
        clock.set(started.expiresAt());
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> submitting = pool.submit(() -> {
                barrier.await();
                return submit(started).status();
            });
            Future<Integer> sweeping = pool.submit(() -> {
                barrier.await();
                return sweep.sweep();
            });
            assertThat(submitting.get()).isEqualTo(200);
            assertThat(sweeping.get()).isBetween(0, 1);
        }
        assertThat(attemptStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(jdbc.sql("select count(*) from grade_revisions").query(Long.class).single()).isEqualTo(1);
    }

    private record Started(CourseExamFixture fixture, String id, Instant expiresAt) {
    }

    private Started started() {
        CourseExamFixture fixture = CourseExamFixture.create(author.session(), learner.session(),
                List.of(singleChoice("Pick A", "1", 0), multipleSelect("Pick two", "2"), written("Essay", "5")), true, Map.of("durationMinutes", 30));
        JsonNode attempt = fixture.start(learner.session()).expect(200).json();
        return new Started(fixture, attempt.path("id").asString(), Instant.parse(attempt.path("expiresAt").asString()));
    }

    private CourseExamFixture fixture(Map<String, Object> schedule) {
        return CourseExamFixture.create(author.session(), learner.session(), List.of(singleChoice("Capital?", "1", 0),
                multipleSelect("Even numbers?", "2"), trueFalse("Water is wet", "1", true), written("Explain osmosis", "5")), true, schedule);
    }

    private ApiResponse save(Started started, UUID questionId, List<String> options, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("selectedOptionIds", options == null ? List.of() : options);
        body.put("writtenText", text);
        return learner.session().put("/attempts/" + started.id() + "/answers/" + questionId, body);
    }

    private ApiResponse submit(Started started) {
        return learner.session().post("/attempts/" + started.id() + "/submit", null);
    }

    private BigDecimal objectiveScore() {
        return jdbc.sql("select objective_score from exam_attempts").query(BigDecimal.class).single();
    }

    private AttemptStatus attemptStatus() {
        return AttemptStatus.fromCode(jdbc.sql("select status from exam_attempts").query(Short.class).single());
    }
}
