package com.saasresearcher.learning.assessments;

import static com.saasresearcher.learning.support.ExamFixtures.multipleSelect;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.written;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.assessments.internal.ExpiredAttemptSweep;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ExamClassFixture;
import com.saasresearcher.learning.support.ExamClassFixture.QuestionKey;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.sql.Timestamp;
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
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class AttemptConcurrencyIT extends PostgresIntegrationTest {

    @Autowired
    private ExpiredAttemptSweep sweep;

    private ApiClient admin;
    private TestUser teacher;
    private TestUser student;
    private ExamClassFixture fixture;
    private String attemptId;
    private Instant expiresAt;

    @BeforeEach
    void startedAttempt() {
        admin = setupAdministrator();
        teacher = createUser(admin, "race.teacher", "race@example.test", "TEACHER");
        student = createUser(admin, "race.student", null, "STUDENT");
        fixture = ExamClassFixture.create(teacher.session(), student.username(),
                List.of(singleChoice("Pick A", "1", 0), multipleSelect("Pick two", "2"), written("Essay", "5")));
        UUID assignment = fixture.assign(teacher.session(), Map.of("durationMinutes", 30));
        JsonNode attempt = student.session().post("/exam-assignments/" + assignment + "/attempts", null).expect(200).json();
        attemptId = attempt.path("id").asString();
        expiresAt = Instant.parse(attempt.path("expiresAt").asString());
    }

    @Test
    void staleRevisionReturnsTheCurrentAnswer() {
        QuestionKey essay = fixture.key("Essay");
        save(essay.questionId(), 0, null, "First draft").expect(200);
        ApiResponse stale = save(essay.questionId(), 0, null, "Other tab");
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.code()).isEqualTo("ANSWER_REVISION_CONFLICT");
        assertThat(stale.json().path("current").path("writtenText").asString()).isEqualTo("First draft");
        assertThat(stale.json().path("current").path("revision").asLong()).isEqualTo(1);
        assertThat(save(essay.questionId(), 1, null, "Second draft").json().path("revision").asLong()).isEqualTo(2);
    }

    @Test
    void answersMustMatchTheQuestionType() {
        QuestionKey single = fixture.key("Pick A");
        assertThat(save(single.questionId(), 0, single.optionIds(), null).status()).isEqualTo(400);
        assertThat(save(single.questionId(), 0, List.of(UUID.randomUUID().toString()), null).status()).isEqualTo(400);
        assertThat(save(fixture.key("Essay").questionId(), 0, null, "x".repeat(20_001)).status()).isEqualTo(400);
        assertThat(save(fixture.key("Essay").questionId(), 0, null, "x".repeat(20_000)).status()).isEqualTo(200);
    }

    @Test
    void submitIsIdempotentAndScoresExactSetsOnly() {
        QuestionKey single = fixture.key("Pick A");
        QuestionKey multiple = fixture.key("Pick two");
        save(single.questionId(), 0, List.of(single.optionIds().get(0)), null).expect(200);
        save(multiple.questionId(), 0, multiple.optionIds(), null).expect(200);

        JsonNode first = submit().expect(200).json();
        JsonNode again = submit().expect(200).json();
        assertThat(again).isEqualTo(first);
        assertThat(first.path("status").asString()).isEqualTo("AWAITING_GRADING");
        assertThat(first.path("finalizedReason").asString()).isEqualTo("SUBMITTED");
        assertThat(objectiveScore()).isEqualByComparingTo("1");
        assertThat(save(single.questionId(), 1, List.of(single.optionIds().get(1)), null).code()).isEqualTo("ATTEMPT_CLOSED");
    }

    @Test
    void writesAtTheDeadlineAreRejectedAndTheAttemptFinalizesFromSavedAnswers() {
        QuestionKey single = fixture.key("Pick A");
        save(single.questionId(), 0, List.of(single.optionIds().get(0)), null).expect(200);

        clock.set(expiresAt.minusMillis(1));
        save(fixture.key("Essay").questionId(), 0, null, "Just in time").expect(200);
        clock.set(expiresAt);
        ApiResponse late = save(single.questionId(), 1, List.of(single.optionIds().get(1)), null);
        assertThat(late.code()).isEqualTo("ATTEMPT_EXPIRED");

        JsonNode attempt = student.session().get("/attempts/" + attemptId).json();
        assertThat(attempt.path("status").asString()).isEqualTo("AWAITING_GRADING");
        assertThat(jdbc.sql("select finalized_reason from exam_attempts").query(String.class).single()).isEqualTo("EXPIRED");
        assertThat(objectiveScore()).isEqualByComparingTo("1");
        assertThat(attempt.path("answers").findValuesAsString("writtenText")).contains("Just in time");
    }

    @Test
    void sweepFinalizesAbandonedAttemptsEvenForDisabledStudents() {
        QuestionKey single = fixture.key("Pick A");
        save(single.questionId(), 0, List.of(single.optionIds().get(0)), null).expect(200);
        admin.put("/users/" + student.id() + "/status", Map.of("status", "DISABLED")).expect(200);
        assertThat(save(single.questionId(), 1, List.of(), null).status()).isEqualTo(401);

        assertThat(sweep.sweep()).isZero();
        clock.set(expiresAt);
        assertThat(sweep.sweep()).isEqualTo(1);
        assertThat(sweep.sweep()).isZero();
        assertThat(jdbc.sql("select status from exam_attempts").query(String.class).single()).isEqualTo("AWAITING_GRADING");
        assertThat(objectiveScore()).isEqualByComparingTo("1");
    }

    @Test
    void suspendedStudentsCannotWriteButKeepAcknowledgedAnswers() {
        QuestionKey single = fixture.key("Pick A");
        save(single.questionId(), 0, List.of(single.optionIds().get(0)), null).expect(200);
        teacher.session().put("/classes/" + fixture.classId() + "/enrollments/" + fixture.enrollmentId(), Map.of("status", "SUSPENDED")).expect(200);
        assertThat(save(single.questionId(), 1, List.of(single.optionIds().get(1)), null).code()).isEqualTo("ENROLLMENT_INACTIVE");
        clock.set(expiresAt);
        sweep.sweep();
        assertThat(objectiveScore()).isEqualByComparingTo("1");
    }

    @Test
    void racingSaveAndSubmitNeverLeaveAnAnswerAfterFinalization() throws Exception {
        QuestionKey essay = fixture.key("Essay");
        for (int round = 0; round < 5; round++) {
            CyclicBarrier barrier = new CyclicBarrier(2);
            long revision = jdbc.sql("select coalesce(max(revision), 0) from attempt_answers").query(Long.class).single();
            try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
                Future<Integer> saving = pool.submit(() -> {
                    barrier.await();
                    return save(essay.questionId(), revision, null, "round").status();
                });
                Future<Integer> submitting = round == 4 ? pool.submit(() -> {
                    barrier.await();
                    return submit().status();
                }) : pool.submit(() -> {
                    barrier.await();
                    return 200;
                });
                assertThat(saving.get()).isIn(200, 409);
                assertThat(submitting.get()).isEqualTo(200);
            }
        }
        Timestamp finalizedAt = jdbc.sql("select finalized_at from exam_attempts").query(Timestamp.class).single();
        Timestamp lastSave = jdbc.sql("select max(saved_at) from attempt_answers").query(Timestamp.class).single();
        assertThat(lastSave).isBeforeOrEqualTo(finalizedAt);
        assertThat(save(essay.questionId(), 99, null, "late").code()).isEqualTo("ATTEMPT_CLOSED");
    }

    @Test
    void untouchedDeadlineDoesNotExtendWhenTheBrowserReconnects() {
        clock.advance(Duration.ofMinutes(10));
        JsonNode reconnect = student.session().get("/attempts/" + attemptId).json();
        assertThat(Instant.parse(reconnect.path("expiresAt").asString())).isEqualTo(expiresAt);
        UUID assignment = UUID.fromString(reconnect.path("assignmentId").asString());
        assertThat(student.session().post("/exam-assignments/" + assignment + "/attempts", null).json().path("expiresAt").asString())
                .isEqualTo(expiresAt.toString());
    }

    private ApiResponse save(UUID questionId, long revision, List<String> options, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("expectedRevision", revision);
        body.put("selectedOptionIds", options == null ? List.of() : options);
        body.put("writtenText", text);
        return student.session().put("/attempts/" + attemptId + "/answers/" + questionId, body);
    }

    private ApiResponse submit() {
        return student.session().post("/attempts/" + attemptId + "/submit", null);
    }

    private java.math.BigDecimal objectiveScore() {
        return jdbc.sql("select objective_score from exam_attempts").query(java.math.BigDecimal.class).single();
    }
}
