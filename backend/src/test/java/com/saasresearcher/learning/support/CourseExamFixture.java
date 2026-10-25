package com.saasresearcher.learning.support;

import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.saveDraft;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.ExamFixtures.draftExam;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * A published course edition with one exam and an enrolled learner. {@code schedule} overrides the exam defaults
 * (self-paced, 60 minutes, one attempt).
 */
public record CourseExamFixture(UUID courseId, UUID versionId, UUID examId, UUID enrollmentId, JsonNode exam) {

    public static CourseExamFixture create(ApiClient author, ApiClient learner, List<Map<String, Object>> questions, boolean required,
            Map<String, Object> schedule) {
        UUID courseId = createCourse(author, "Science " + UUID.randomUUID());
        saveDraft(author, courseId, "Science", List.of(section("Unit", List.of(lesson("Intro", "", true)))));
        JsonNode exam = draftExam(author, courseId, "Quiz", "50", questions, required, schedule);
        JsonNode edition = publish(author, courseId);
        UUID enrollmentId = EnrollmentFixtures.enroll(author, learner, courseId);
        return new CourseExamFixture(courseId, UUID.fromString(edition.path("versionId").asString()), UUID.fromString(exam.path("id").asString()),
                enrollmentId, exam);
    }

    /** Option IDs of a question by its prompt, in authoring order, plus the question ID. */
    public QuestionKey key(String prompt) {
        for (JsonNode question : exam.path("questions")) {
            if (question.path("prompt").asString().equals(prompt)) {
                return new QuestionKey(UUID.fromString(question.path("id").asString()), question.path("options").findValuesAsString("id"));
            }
        }
        throw new IllegalArgumentException(prompt);
    }

    public ApiClient.ApiResponse start(ApiClient learner) {
        return learner.post("/exams/" + examId + "/attempts", null);
    }

    public record QuestionKey(UUID questionId, List<String> optionIds) {
    }
}
