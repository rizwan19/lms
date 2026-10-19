package com.saasresearcher.learning.assessments;

import static com.saasresearcher.learning.support.ClassFixtures.createClass;
import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.draft;
import static com.saasresearcher.learning.support.CourseFixtures.draftRevision;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.ExamFixtures.createExam;
import static com.saasresearcher.learning.support.ExamFixtures.multipleSelect;
import static com.saasresearcher.learning.support.ExamFixtures.option;
import static com.saasresearcher.learning.support.ExamFixtures.publishExam;
import static com.saasresearcher.learning.support.ExamFixtures.publishedExam;
import static com.saasresearcher.learning.support.ExamFixtures.question;
import static com.saasresearcher.learning.support.ExamFixtures.saveExam;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.trueFalse;
import static com.saasresearcher.learning.support.ExamFixtures.written;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ExamPublicationIT extends PostgresIntegrationTest {

    private ApiClient admin;
    private TestUser author;
    private UUID courseId;

    @BeforeEach
    void course() {
        admin = setupAdministrator();
        author = createUser(admin, "exam.author", "exam@example.test", "TEACHER");
        courseId = createCourse(author.session(), "Algebra");
    }

    @Test
    void allFourQuestionTypesPublishWithNormalizedKeys() {
        JsonNode edition = publishedExam(author.session(), courseId, "Unit test", "60", List.of(
                singleChoice("2 + 2?", "1", 1), multipleSelect("Primes?", "2.5"), trueFalse("Zero is even", "1", true),
                written("Explain why", "5")));

        assertThat(edition.path("edition").asInt()).isEqualTo(1);
        JsonNode questions = edition.path("questions");
        assertThat(questions).hasSize(4);
        assertThat(questions.get(1).path("points").decimalValue()).isEqualByComparingTo("2.50");
        assertThat(questions.get(2).path("options").findValuesAsString("label")).containsExactly("True", "False");
        assertThat(questions.get(3).path("options")).isEmpty();
    }

    @Test
    void incompleteKeysBlockPublicationButNotDrafts() {
        UUID examId = createExam(author.session(), courseId, "Keys");
        Map<String, Object> noCorrect = question("SINGLE_CHOICE", "Pick", "1", List.of(option("A", false), option("B", false)));
        Map<String, Object> twoCorrect = question("SINGLE_CHOICE", "Pick one", "1", List.of(option("A", true), option("B", true)));
        Map<String, Object> noneSelected = question("MULTIPLE_SELECT", "Pick some", "1", List.of(option("A", false), option("B", false)));
        saveExam(author.session(), examId, "Keys", "50", List.of(noCorrect, twoCorrect, noneSelected)).expect(200);

        ApiResponse publish = author.session().post("/exams/" + examId + "/publish",
                Map.of("expectedRevision", author.session().get("/exams/" + examId + "/draft").json().path("revision").asLong()));
        assertThat(publish.status()).isEqualTo(400);
        assertThat(publish.json().path("fieldErrors").propertyNames())
                .contains("questions[0].options", "questions[1].options", "questions[2].options");
    }

    @Test
    void pointTextAndQuestionLimitsAreEnforced() {
        UUID examId = createExam(author.session(), courseId, "Limits");
        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(written("Q" + i, "1"));
        }
        assertThat(saveExam(author.session(), examId, "Limits", "50", tooMany).json().path("fieldErrors").has("questions")).isTrue();
        assertThat(saveExam(author.session(), examId, "Limits", "50", List.of(written("x".repeat(10_001), "1")))
                .json().path("fieldErrors").has("questions[0].prompt")).isTrue();
        assertThat(saveExam(author.session(), examId, "Limits", "50", List.of(written("Zero", "0"), written("Precise", "1.234"))).json()
                .path("fieldErrors").propertyNames()).contains("questions[0].points", "questions[1].points");
        assertThat(saveExam(author.session(), examId, "Limits", "101", List.of()).json().path("fieldErrors").has("passPercent")).isTrue();
        ApiResponse empty = author.session().post("/exams/" + examId + "/publish",
                Map.of("expectedRevision", author.session().get("/exams/" + examId + "/draft").json().path("revision").asLong()));
        assertThat(empty.json().path("fieldErrors").has("questions")).isTrue();
    }

    @Test
    void publishedEditionsNeverChange() {
        JsonNode first = publishedExam(author.session(), courseId, "Quiz", "50", List.of(singleChoice("Wrnog spelling", "1", 0)));
        UUID examId = UUID.fromString(first.path("examId").asString());
        String firstVersion = first.path("versionId").asString();

        author.session().post("/exams/" + examId + "/draft", Map.of("fromVersionId", firstVersion)).expect(201);
        saveExam(author.session(), examId, "Quiz", "50", List.of(singleChoice("Right spelling", "1", 0))).expect(200);
        assertThat(publishExam(author.session(), examId).path("edition").asInt()).isEqualTo(2);

        JsonNode original = author.session().get("/exams/" + examId + "/editions/" + firstVersion).expect(200).json();
        assertThat(original.path("questions").get(0).path("prompt").asString()).isEqualTo("Wrnog spelling");
        assertThat(author.session().get("/exams/" + examId).json().path("editions")).hasSize(2);
    }

    @Test
    void onlyCourseAuthorsManageExams() {
        UUID examId = createExam(author.session(), courseId, "Private");
        TestUser outsider = createUser(admin, "outsider", "outsider@example.test", "TEACHER");
        assertThat(outsider.session().get("/exams/" + examId + "/draft").status()).isEqualTo(404);
        assertThat(outsider.session().post("/exams", Map.of("courseId", courseId, "title", "Mine")).status()).isEqualTo(404);
        assertThat(outsider.session().get("/exams?courseId=" + courseId).status()).isEqualTo(404);
        assertThat(admin.get("/exams/" + examId).status()).isEqualTo(200);
    }

    @Test
    void requiredExamsMustBePublishedEditionsOfTheCourse() {
        JsonNode exam = publishedExam(author.session(), courseId, "Final", "50", List.of(singleChoice("Q", "1", 0)));
        UUID draftOnly = createExam(author.session(), courseId, "Unpublished");
        String draftVersion = author.session().get("/exams/" + draftOnly + "/draft").json().path("versionId").asString();
        UUID otherCourse = createCourse(author.session(), "Other");
        String foreign = publishedExam(author.session(), otherCourse, "Foreign", "50", List.of(singleChoice("Q", "1", 0))).path("versionId").asString();
        List<Map<String, Object>> sections = List.of(section("S", List.of(lesson("L", "", true))));

        for (String invalid : List.of(draftVersion, foreign)) {
            ApiResponse rejected = author.session().put("/courses/" + courseId + "/draft",
                    draft(draftRevision(author.session(), courseId), "Algebra", sections, List.of(invalid)));
            assertThat(rejected.json().path("fieldErrors").has("requiredExamVersionIds")).isTrue();
        }
        author.session().put("/courses/" + courseId + "/draft",
                draft(draftRevision(author.session(), courseId), "Algebra", sections, List.of(exam.path("versionId").asString()))).expect(200);
        JsonNode published = publish(author.session(), courseId);
        assertThat(published.path("requiredExamVersionIds").get(0).asString()).isEqualTo(exam.path("versionId").asString());
    }

    @Test
    void activationMaterializesRequiredAssignmentsWithValidatedSchedules() {
        JsonNode mixed = publishedExam(author.session(), courseId, "Midterm", "50", List.of(singleChoice("Q", "1", 0), written("Essay", "4")));
        List<Map<String, Object>> sections = List.of(section("S", List.of(lesson("L", "", true))));
        author.session().put("/courses/" + courseId + "/draft",
                draft(draftRevision(author.session(), courseId), "Algebra", sections, List.of(mixed.path("versionId").asString()))).expect(200);
        UUID versionId = UUID.fromString(publish(author.session(), courseId).path("versionId").asString());
        UUID classId = createClass(author.session(), versionId, "Algebra A", null, null);
        assertThat(author.session().get("/exam-assignments?classId=" + classId).json()).isEmpty();

        author.session().post("/classes/" + classId + "/activate", null).expect(200);
        JsonNode assignment = author.session().get("/exam-assignments?classId=" + classId).expect(200).json().get(0);
        assertThat(assignment.path("required").asBoolean()).isTrue();
        assertThat(assignment.path("schedule").path("durationMinutes").asInt()).isEqualTo(60);
        assertThat(assignment.path("schedule").path("resultPolicy").asString()).isEqualTo("TEACHER_PUBLISH");
        String id = assignment.path("id").asString();
        long revision = assignment.path("revision").asLong();

        assertThat(update(id, revision, "2026-11-01T09:00:00+06:00", "2026-11-01T09:00:00+06:00", 60, null).json()
                .path("fieldErrors").has("closesAt")).isTrue();
        assertThat(update(id, revision, null, null, null, null).json().path("fieldErrors").has("closesAt")).isTrue();
        assertThat(update(id, revision, null, null, 60, "AUTO_PUBLISH").json().path("fieldErrors").has("resultPolicy")).isTrue();
        Map<String, Object> afterSubmission = schedule(revision, null, "2026-11-02T00:00:00Z", 30, null);
        afterSubmission.put("feedbackPolicy", "AFTER_SUBMISSION");
        assertThat(author.session().put("/exam-assignments/" + id, afterSubmission).json().path("fieldErrors").has("feedbackPolicy")).isTrue();

        JsonNode saved = update(id, revision, "2026-11-01T09:00:00+06:00", "2026-11-01T11:00:00+06:00", 90, null).expect(200).json();
        assertThat(saved.path("schedule").path("opensAt").asString()).isEqualTo("2026-11-01T03:00:00Z");
        assertThat(saved.path("schedule").path("closesAt").asString()).isEqualTo("2026-11-01T05:00:00Z");
        assertThat(update(id, revision, null, null, 60, null).code()).isEqualTo("REVISION_CONFLICT");
    }

    @Test
    void practiceAssignmentsUseThisCoursesExamsOnce() {
        List<Map<String, Object>> sections = List.of(section("S", List.of(lesson("L", "", true))));
        author.session().put("/courses/" + courseId + "/draft", draft(draftRevision(author.session(), courseId), "Algebra", sections)).expect(200);
        UUID versionId = UUID.fromString(publish(author.session(), courseId).path("versionId").asString());
        UUID classId = createClass(author.session(), versionId, "Algebra A", null, null);
        String practice = publishedExam(author.session(), courseId, "Practice", "50", List.of(singleChoice("Q", "1", 0))).path("versionId").asString();
        UUID otherCourse = createCourse(author.session(), "Other");
        String foreign = publishedExam(author.session(), otherCourse, "Foreign", "50", List.of(singleChoice("Q", "1", 0))).path("versionId").asString();
        assertThat(author.session().get("/exam-assignments/available?classId=" + classId).json().findValuesAsString("title")).containsExactly("Practice");

        Map<String, Object> request = new HashMap<>(Map.of("classId", classId, "examVersionId", practice, "durationMinutes", 20,
                "maxAttempts", 3, "feedbackPolicy", "AFTER_SUBMISSION"));
        JsonNode created = author.session().post("/exam-assignments", request).expect(201).json();
        assertThat(created.path("required").asBoolean()).isFalse();
        assertThat(created.path("schedule").path("resultPolicy").asString()).isEqualTo("AUTO_PUBLISH");
        assertThat(author.session().post("/exam-assignments", request).code()).isEqualTo("ALREADY_ASSIGNED");
        request.put("examVersionId", foreign);
        assertThat(author.session().post("/exam-assignments", request).json().path("fieldErrors").has("examVersionId")).isTrue();
        request.put("maxAttempts", 11);
        request.put("examVersionId", practice);
        assertThat(author.session().post("/exam-assignments", request).json().path("fieldErrors").has("maxAttempts")).isTrue();
    }

    private ApiResponse update(String id, long revision, String opensAt, String closesAt, Integer duration, String resultPolicy) {
        return author.session().put("/exam-assignments/" + id, schedule(revision, opensAt, closesAt, duration, resultPolicy));
    }

    private static Map<String, Object> schedule(long revision, String opensAt, String closesAt, Integer duration, String resultPolicy) {
        Map<String, Object> body = new HashMap<>();
        body.put("expectedRevision", revision);
        body.put("opensAt", opensAt);
        body.put("closesAt", closesAt);
        body.put("durationMinutes", duration);
        body.put("resultPolicy", resultPolicy);
        return body;
    }
}
