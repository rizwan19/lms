package com.saasresearcher.learning.assessments;

import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.saveDraft;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.ExamFixtures.draftExam;
import static com.saasresearcher.learning.support.ExamFixtures.saveExam;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.written;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.CourseExamFixture;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Exams of course editions, self-paced or scheduled; content and the required flag freeze with the edition. */
class CourseExamScheduleIT extends PostgresIntegrationTest {

    private TestUser owner;
    private TestUser author;
    private UUID courseId;

    @BeforeEach
    void draftCourse() {
        owner = setupOwner();
        author = approvedTeacher("author", owner);
        courseId = createCourse(author.session(), "Algebra");
        saveDraft(author.session(), courseId, "Algebra", List.of(section("S", List.of(lesson("L", "", true)))));
    }

    @Test
    void defaultsFollowTheQuestionTypes() {
        JsonNode quiz = create(true, Map.of()).expect(201).json();
        assertThat(quiz.path("mode").asString()).isEqualTo("SELF_PACED");
        assertThat(quiz.path("durationMinutes").asInt()).isEqualTo(60);
        assertThat(quiz.path("maxAttempts").asInt()).isEqualTo(1);
        assertThat(quiz.path("resultPolicy").asString()).isEqualTo("AUTO_PUBLISH");
        assertThat(quiz.path("draftEdition").asBoolean()).isTrue();

        UUID examId = UUID.fromString(quiz.path("id").asString());
        saveExam(author.session(), examId, "Midterm", "50", List.of(singleChoice("Q", "1", 0), written("Essay", "4"))).expect(200);
        JsonNode mixed = author.session().get("/courses/" + courseId + "/exams").json().get(0);
        assertThat(mixed.path("mixed").asBoolean()).isTrue();
        assertThat(mixed.path("resultPolicy").asString()).as("written questions need the author").isEqualTo("TEACHER_PUBLISH");
        ApiResponse auto = schedule(examId, mixed.path("revision").asLong(), Map.of("resultPolicy", "AUTO_PUBLISH"));
        assertThat(auto.json().path("fieldErrors").has("resultPolicy")).isTrue();
    }

    @Test
    void scheduleRulesAreValidated() {
        Instant opens = Instant.parse("2030-11-01T03:00:00Z");
        assertThat(fields(Map.of("mode", "SELF_PACED", "opensAt", opens.toString()), true)).contains("mode");
        assertThat(fields(Map.of("mode", "SCHEDULED"), true)).contains("opensAt", "closesAt");
        assertThat(fields(Map.of("mode", "SCHEDULED", "opensAt", opens.toString(), "closesAt", opens.toString()), true)).contains("closesAt");
        assertThat(fields(Map.of("durationMinutes", 241), true)).contains("durationMinutes");
        assertThat(fields(Map.of("durationMinutes", 0), true)).contains("durationMinutes");
        assertThat(fields(Map.of("maxAttempts", 11), true)).contains("maxAttempts");
        assertThat(fields(Map.of("feedbackPolicy", "AFTER_CLOSE"), true)).contains("feedbackPolicy");
        assertThat(fields(Map.of("feedbackPolicy", "AFTER_SUBMISSION"), true)).contains("feedbackPolicy");
        assertThat(create(false, Map.of("feedbackPolicy", "AFTER_SUBMISSION", "maxAttempts", 3)).status()).isEqualTo(201);
        assertThat(author.session().post("/courses/" + courseId + "/exams", Map.of("title", " ", "required", true))
                .json().path("fieldErrors").has("title")).isTrue();
    }

    @Test
    void publicationFreezesContentAndRequiredFlagButNotTheSchedule() {
        JsonNode created = draftExam(author.session(), courseId, "Quiz", "50", List.of(singleChoice("Q", "1", 0)), true, Map.of());
        UUID examId = UUID.fromString(created.path("id").asString());
        JsonNode edition = publish(author.session(), courseId);
        JsonNode frozen = author.session().get("/courses/" + courseId + "/exams").expect(200).json().get(0);
        assertThat(frozen.path("id").asString()).as("the draft's exam becomes the edition's").isEqualTo(examId.toString());
        assertThat(frozen.path("draftEdition").asBoolean()).isFalse();
        assertThat(frozen.path("courseVersionId").asString()).isEqualTo(edition.path("versionId").asString());

        long revision = frozen.path("revision").asLong();
        assertThat(schedule(examId, revision, Map.of("required", false)).code()).isEqualTo("EXAM_FROZEN");
        JsonNode rescheduled = schedule(examId, revision, Map.of("durationMinutes", 90, "maxAttempts", 2)).expect(200).json();
        assertThat(rescheduled.path("durationMinutes").asInt()).isEqualTo(90);
        assertThat(schedule(examId, revision, Map.of("durationMinutes", 30)).code()).isEqualTo("REVISION_CONFLICT");
        assertThat(create(false, Map.of()).code()).as("no draft to add to").isEqualTo("NO_DRAFT");
    }

    @Test
    void correctionDraftsCopyTheEditionsExamsWithNewIds() {
        JsonNode created = draftExam(author.session(), courseId, "Quiz", "50", List.of(singleChoice("Q", "1", 0)), true, Map.of("maxAttempts", 3));
        JsonNode edition = publish(author.session(), courseId);
        author.session().post("/courses/" + courseId + "/draft", Map.of("fromVersionId", edition.path("versionId").asString())).expect(201);
        JsonNode copied = author.session().get("/courses/" + courseId + "/exams").json().get(0);
        assertThat(copied.path("draftEdition").asBoolean()).isTrue();
        assertThat(copied.path("id").asString()).isNotEqualTo(created.path("id").asString());
        assertThat(copied.path("maxAttempts").asInt()).isEqualTo(3);
        assertThat(copied.path("questionCount").asInt()).isEqualTo(1);
        author.session().delete("/exams/" + copied.path("id").asString()).expect(204);
        assertThat(author.session().get("/courses/" + courseId + "/exams").json()).isEmpty();
        assertThat(author.session().get("/courses/" + courseId + "/exams?versionId=" + edition.path("versionId").asString()).json()).hasSize(1);
    }

    @Test
    void scheduledWindowsAreServerSideAndSelfPacedExamsAreAlwaysOpen() {
        TestUser learner = registerStudent("learner");
        Instant opens = clock.instant().plus(Duration.ofHours(1));
        CourseExamFixture scheduled = CourseExamFixture.create(author.session(), learner.session(), List.of(singleChoice("Q", "1", 0)), true,
                Map.of("mode", "SCHEDULED", "opensAt", opens.toString(), "closesAt", opens.plus(Duration.ofHours(2)).toString()));
        assertThat(scheduled.start(learner.session()).code()).isEqualTo("EXAM_NOT_OPEN");
        clock.set(opens.minusMillis(1));
        assertThat(scheduled.start(learner.session()).code()).isEqualTo("EXAM_NOT_OPEN");
        clock.set(opens);
        String attempt = scheduled.start(learner.session()).expect(200).json().path("id").asString();
        learner.session().post("/attempts/" + attempt + "/submit", null).expect(200);

        CourseExamFixture selfPaced = CourseExamFixture.create(author.session(), learner.session(), List.of(singleChoice("Q", "1", 0)), false,
                Map.of("maxAttempts", 2));
        clock.advance(Duration.ofDays(400));
        assertThat(selfPaced.start(learner.session()).status()).isEqualTo(200);
        JsonNode exams = learner.session().get("/me/enrollments/" + selfPaced.enrollmentId() + "/exams").expect(200).json();
        assertThat(exams.get(0).path("mode").asString()).isEqualTo("SELF_PACED");
        assertThat(exams.get(0).path("canStart").asBoolean()).isTrue();
    }

    @Test
    void boundariesHoldInDhakaAndUtc() {
        TestUser learner = registerStudent("dhaka");
        Map<String, Object> schedule = new HashMap<>(Map.of("mode", "SCHEDULED", "opensAt", "2030-11-01T09:00:00+06:00",
                "closesAt", "2030-11-01T11:00:00+06:00"));
        CourseExamFixture fixture = CourseExamFixture.create(author.session(), learner.session(), List.of(singleChoice("Q", "1", 0)), true, schedule);
        JsonNode stored = author.session().get("/courses/" + fixture.courseId() + "/exams").json().get(0);
        assertThat(stored.path("opensAt").asString()).isEqualTo("2030-11-01T03:00:00Z");
        assertThat(stored.path("closesAt").asString()).isEqualTo("2030-11-01T05:00:00Z");
        clock.set(Instant.parse("2030-11-01T04:59:59Z"));
        assertThat(fixture.start(learner.session()).status()).isEqualTo(200);
        clock.set(Instant.parse("2030-11-01T05:00:00Z"));
        TestUser late = registerStudent("late");
        assertThat(learner.session().get("/me/enrollments/" + fixture.enrollmentId() + "/exams").json().get(0).path("canStart").asBoolean()).isFalse();
        assertThat(late).isNotNull();
    }

    private ApiResponse create(boolean required, Map<String, Object> schedule) {
        Map<String, Object> body = new HashMap<>(Map.of("title", "Quiz", "required", required));
        body.putAll(schedule);
        return author.session().post("/courses/" + courseId + "/exams", body);
    }

    private ApiResponse schedule(UUID examId, long revision, Map<String, Object> changes) {
        Map<String, Object> body = new HashMap<>(Map.of("expectedRevision", revision));
        body.putAll(changes);
        return author.session().put("/exams/" + examId + "/schedule", body);
    }

    private java.util.Collection<String> fields(Map<String, Object> schedule, boolean required) {
        return create(required, schedule).json().path("fieldErrors").propertyNames();
    }
}
