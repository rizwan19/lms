package com.saasresearcher.learning.assessments;

import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.saveDraft;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.ExamFixtures.createExam;
import static com.saasresearcher.learning.support.ExamFixtures.draftExam;
import static com.saasresearcher.learning.support.ExamFixtures.multipleSelect;
import static com.saasresearcher.learning.support.ExamFixtures.option;
import static com.saasresearcher.learning.support.ExamFixtures.question;
import static com.saasresearcher.learning.support.ExamFixtures.saveExam;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.trueFalse;
import static com.saasresearcher.learning.support.ExamFixtures.written;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Exam content is edited in the course draft by the course author only and freezes with the published edition. */
class ExamAuthoringIT extends PostgresIntegrationTest {

    private TestUser owner;
    private TestUser author;
    private UUID courseId;

    @BeforeEach
    void course() {
        owner = setupOwner();
        author = approvedTeacher("exam.author", owner);
        courseId = createCourse(author.session(), "Algebra");
        saveDraft(author.session(), courseId, "Algebra", List.of(section("S", List.of(lesson("L", "", true)))));
    }

    @Test
    void allFourQuestionTypesSaveWithNormalizedKeys() {
        JsonNode exam = draftExam(author.session(), courseId, "Unit test", "60", List.of(
                singleChoice("2 + 2?", "1", 1), multipleSelect("Primes?", "2.5"), trueFalse("Zero is even", "1", true), written("Explain why", "5")),
                true, Map.of());

        assertThat(exam.path("draftEdition").asBoolean()).isTrue();
        JsonNode questions = exam.path("questions");
        assertThat(questions).hasSize(4);
        assertThat(questions.get(1).path("points").decimalValue()).isEqualByComparingTo("2.50");
        assertThat(questions.get(2).path("options").findValuesAsString("label")).containsExactly("True", "False");
        assertThat(questions.get(3).path("options")).isEmpty();
        assertThat(publish(author.session(), courseId).path("edition").asInt()).isEqualTo(1);
    }

    @Test
    void incompleteKeysBlockCoursePublicationButNotSaves() {
        UUID examId = createExam(author.session(), courseId, "Keys", true, Map.of());
        Map<String, Object> noCorrect = question("SINGLE_CHOICE", "Pick", "1", List.of(option("A", false), option("B", false)));
        Map<String, Object> twoCorrect = question("SINGLE_CHOICE", "Pick one", "1", List.of(option("A", true), option("B", true)));
        Map<String, Object> noneSelected = question("MULTIPLE_SELECT", "Pick some", "1", List.of(option("A", false), option("B", false)));
        saveExam(author.session(), examId, "Keys", "50", List.of(noCorrect, twoCorrect, noneSelected)).expect(200);
        clock.advance(Duration.ofSeconds(1));
        createExam(author.session(), courseId, "Empty", false, Map.of());

        long revision = author.session().get("/courses/" + courseId + "/draft").json().path("revision").asLong();
        ApiResponse publish = author.session().post("/courses/" + courseId + "/publish", Map.of("expectedRevision", revision));
        assertThat(publish.status()).isEqualTo(400);
        assertThat(publish.json().path("fieldErrors").propertyNames())
                .contains("exams[0].questions[0].options", "exams[0].questions[1].options", "exams[0].questions[2].options");
    }

    @Test
    void pointTextAndQuestionLimitsAreEnforced() {
        UUID examId = createExam(author.session(), courseId, "Limits", false, Map.of());
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
    }

    @Test
    void publishedExamsNeverChangeAndCorrectionDraftsCopyThemWithNewIds() {
        JsonNode first = draftExam(author.session(), courseId, "Quiz", "50", List.of(singleChoice("Wrnog spelling", "1", 0)), true, Map.of());
        UUID examId = UUID.fromString(first.path("id").asString());
        JsonNode edition = publish(author.session(), courseId);
        assertThat(saveExam(author.session(), examId, "Quiz", "50", List.of(singleChoice("Right spelling", "1", 0))).code()).isEqualTo("EXAM_FROZEN");
        assertThat(author.session().delete("/exams/" + examId).code()).isEqualTo("EXAM_FROZEN");

        author.session().post("/courses/" + courseId + "/draft", Map.of("fromVersionId", edition.path("versionId").asString())).expect(201);
        JsonNode copy = author.session().get("/courses/" + courseId + "/exams").expect(200).json().get(0);
        UUID copyId = UUID.fromString(copy.path("id").asString());
        assertThat(copyId).isNotEqualTo(examId);
        JsonNode copied = author.session().get("/exams/" + copyId).expect(200).json();
        assertThat(copied.path("questions").get(0).path("id").asString()).isNotEqualTo(first.path("questions").get(0).path("id").asString());

        Map<String, Object> corrected = singleChoice("Right spelling", "1", 0);
        corrected.put("id", copied.path("questions").get(0).path("id").asString());
        JsonNode saved = saveExam(author.session(), copyId, "Quiz", "50", List.of(corrected)).expect(200).json();
        assertThat(saved.path("questions").get(0).path("id").asString()).as("kept IDs survive saves").isEqualTo(corrected.get("id"));

        JsonNode original = author.session().get("/exams/" + examId).expect(200).json();
        assertThat(original.path("draftEdition").asBoolean()).isFalse();
        assertThat(original.path("questions").get(0).path("prompt").asString()).isEqualTo("Wrnog spelling");
    }

    @Test
    void onlyTheCourseAuthorManagesExams() {
        UUID examId = createExam(author.session(), courseId, "Private", true, Map.of());
        TestUser outsider = approvedTeacher("outsider", owner);
        assertThat(outsider.session().get("/exams/" + examId).status()).isEqualTo(404);
        assertThat(outsider.session().put("/exams/" + examId, Map.of("expectedRevision", 0, "title", "Mine", "questions", List.of())).status())
                .isEqualTo(404);
        assertThat(outsider.session().delete("/exams/" + examId).status()).isEqualTo(404);
        assertThat(outsider.session().post("/courses/" + courseId + "/exams", Map.of("title", "Mine", "required", true)).status()).isEqualTo(404);
        assertThat(outsider.session().get("/courses/" + courseId + "/exams").status()).isEqualTo(404);
        assertThat(owner.session().get("/exams/" + examId).status()).as("no workspace bypass").isEqualTo(404);
    }
}
