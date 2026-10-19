package com.saasresearcher.learning.assessments;

import static com.saasresearcher.learning.support.ExamFixtures.multipleSelect;
import static com.saasresearcher.learning.support.ExamFixtures.saveExam;
import static com.saasresearcher.learning.support.ExamFixtures.publishExam;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.trueFalse;
import static com.saasresearcher.learning.support.ExamFixtures.written;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ExamClassFixture;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

class AttemptStartIT extends PostgresIntegrationTest {

    private ApiClient admin;
    private TestUser teacher;
    private TestUser student;
    private ExamClassFixture fixture;

    @BeforeEach
    void scheduledExam() {
        admin = setupAdministrator();
        teacher = createUser(admin, "attempt.teacher", "attempt@example.test", "TEACHER");
        student = createUser(admin, "attempt.student", null, "STUDENT");
        clock.set(Instant.parse("2030-01-01T10:00:00Z"));
        fixture = ExamClassFixture.create(teacher.session(), student.username(), List.of(singleChoice("Capital?", "1", 0),
                multipleSelect("Even numbers?", "2"), trueFalse("Water is wet", "1", true), written("Explain osmosis", "5")));
    }

    @Test
    void scheduleBoundariesAreServerSide() {
        Instant opens = clock.instant().plus(Duration.ofHours(1));
        UUID assignment = fixture.assign(teacher.session(), Map.of("opensAt", opens.toString(), "closesAt", opens.plus(Duration.ofHours(2)).toString()));

        assertThat(start(student.session(), assignment).code()).isEqualTo("EXAM_NOT_OPEN");
        clock.set(opens);
        assertThat(start(student.session(), assignment).status()).isEqualTo(200);
    }

    @Test
    void deadlineIsTheEarlierOfDurationAndClosing() {
        Instant closes = clock.instant().plus(Duration.ofMinutes(30));
        UUID assignment = fixture.assign(teacher.session(), Map.of("closesAt", closes.toString(), "durationMinutes", 60));
        JsonNode attempt = start(student.session(), assignment).expect(200).json();
        assertThat(Instant.parse(attempt.path("expiresAt").asString())).isEqualTo(closes);
        assertThat(Instant.parse(attempt.path("serverNow").asString())).isEqualTo(clock.instant());
    }

    @Test
    void simultaneousStartsReturnTheSameAttempt() throws Exception {
        UUID assignment = fixture.assign(teacher.session(), Map.of());
        ApiClient secondTab = login(student.username(), PASSWORD);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<String>> ids = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (ApiClient tab : List.of(student.session(), secondTab)) {
                ids.add(pool.submit(() -> {
                    barrier.await();
                    return start(tab, assignment).expect(200).json().path("id").asString();
                }));
            }
            assertThat(ids.get(0).get()).isEqualTo(ids.get(1).get());
        }
        assertThat(jdbc.sql("select count(*) from exam_attempts").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void attemptLimitIsEnforced() {
        UUID assignment = fixture.assign(teacher.session(), Map.of("maxAttempts", 2));
        String first = start(student.session(), assignment).json().path("id").asString();
        student.session().post("/attempts/" + first + "/submit", null).expect(200);
        String second = start(student.session(), assignment).expect(200).json().path("id").asString();
        assertThat(second).isNotEqualTo(first);
        student.session().post("/attempts/" + second + "/submit", null).expect(200);
        assertThat(start(student.session(), assignment).code()).isEqualTo("ATTEMPT_LIMIT_REACHED");
    }

    @Test
    void snapshotOrderIsStableAndLaterEditionsCannotChangeIt() {
        UUID assignment = fixture.assign(teacher.session(), Map.of());
        JsonNode attempt = start(student.session(), assignment).json();
        String attemptId = attempt.path("id").asString();

        UUID examId = UUID.fromString(fixture.exam().path("examId").asString());
        teacher.session().post("/exams/" + examId + "/draft", Map.of("fromVersionId", fixture.exam().path("versionId").asString())).expect(201);
        saveExam(teacher.session(), examId, "Quiz", "50", List.of(singleChoice("Changed prompt", "1", 0))).expect(200);
        publishExam(teacher.session(), examId);

        JsonNode again = student.session().get("/attempts/" + attemptId).expect(200).json();
        assertThat(again.path("questions")).isEqualTo(attempt.path("questions"));
        assertThat(again.path("questions").findValuesAsString("prompt")).doesNotContain("Changed prompt").hasSize(4);
    }

    @Test
    void studentPayloadsNeverContainKeysOrGradingNotes() {
        UUID assignment = fixture.assign(teacher.session(), Map.of());
        String body = start(student.session(), assignment).text();
        assertThat(body).doesNotContain("\"correct\"", "gradingNotes", "explanation", "Because.", "autoPoints", "objectiveScore");
        String list = student.session().get("/me/enrollments/" + fixture.enrollmentId() + "/exams").expect(200).text();
        assertThat(list).doesNotContain("\"correct\"", "objectiveScore");
    }

    @Test
    void onlyTheEnrolledStudentCanStartOrRead() {
        UUID assignment = fixture.assign(teacher.session(), Map.of());
        TestUser outsider = createUser(admin, "outsider", null, "STUDENT");
        assertThat(start(outsider.session(), assignment).status()).isEqualTo(404);
        String attemptId = start(student.session(), assignment).json().path("id").asString();
        assertThat(outsider.session().get("/attempts/" + attemptId).status()).isEqualTo(404);
        assertThat(teacher.session().get("/attempts/" + attemptId).status()).isEqualTo(404);

        teacher.session().put("/classes/" + fixture.classId() + "/enrollments/" + fixture.enrollmentId(), Map.of("status", "SUSPENDED")).expect(200);
        clock.advance(Duration.of(1, ChronoUnit.MINUTES));
        assertThat(start(student.session(), assignment).code()).isEqualTo("ENROLLMENT_INACTIVE");
    }

    private static ApiResponse start(ApiClient session, UUID assignment) {
        return session.post("/exam-assignments/" + assignment + "/attempts", null);
    }
}
