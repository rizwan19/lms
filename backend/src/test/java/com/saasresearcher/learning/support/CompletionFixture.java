package com.saasresearcher.learning.support;

import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.saveDraft;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.ExamFixtures.draftExam;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.written;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * A course edition requiring two lessons (a third is optional) and one mixed exam (1-point single choice plus a 4-point
 * essay, pass mark 50%). The learner is enrolled; helpers drive study, attempts and grading through the API.
 */
public record CompletionFixture(ApiClient author, ApiClient learner, UUID courseId, UUID enrollmentId, List<String> lessonIds, UUID examId,
        JsonNode exam) {

    public static CompletionFixture create(ApiClient author, ApiClient learner, int maxAttempts) {
        String title = "Completion course " + UUID.randomUUID();
        UUID courseId = createCourse(author, title);
        saveDraft(author, courseId, title, List.of(section("S", List.of(lesson("One", "", true), lesson("Two", "", true), lesson("Extra", "", false)))));
        JsonNode exam = draftExam(author, courseId, "Final", "50", List.of(singleChoice("Pick A", "1", 0), written("Essay", "4")), true,
                Map.of("maxAttempts", maxAttempts));
        JsonNode edition = publish(author, courseId);
        UUID enrollmentId = EnrollmentFixtures.enroll(author, learner, courseId);
        return new CompletionFixture(author, learner, courseId, enrollmentId, edition.path("sections").get(0).path("lessons").findValuesAsString("id"),
                UUID.fromString(exam.path("id").asString()), exam);
    }

    public void completeLesson(int index, boolean completed) {
        long revision = learner.get("/me/enrollments/" + enrollmentId + "/content").expect(200).json().path("progress").valueStream()
                .filter(node -> node.path("lessonId").asString().equals(lessonIds.get(index)))
                .map(node -> node.path("revision").asLong()).findFirst().orElse(0L);
        completeLessonResponse(index, completed, revision).expect(200);
    }

    public ApiClient.ApiResponse completeLessonResponse(int index, boolean completed, long revision) {
        return learner.put("/enrollments/" + enrollmentId + "/lessons/" + lessonIds.get(index) + "/progress",
                Map.of("expectedRevision", revision, "completed", completed, "resumeSeconds", 0));
    }

    /** Submits an attempt with the single-choice answer correct; returns the attempt ID (essay ungraded). */
    public String submitAttempt() {
        String attemptId = learner.post("/exams/" + examId + "/attempts", null).expect(200).json().path("id").asString();
        JsonNode single = question("Pick A");
        learner.put("/attempts/" + attemptId + "/answers/" + single.path("id").asString(),
                Map.of("selectedOptionIds", List.of(single.path("options").get(0).path("id").asString()))).expect(200);
        learner.post("/attempts/" + attemptId + "/submit", null).expect(200);
        return attemptId;
    }

    /** Grades the essay, finalizes and publishes (opening a regrade first when a result is already published). */
    public void publishEssayGrade(String attemptId, String points) {
        String base = "/grading/attempts/" + attemptId;
        JsonNode view = author.get(base).expect(200).json();
        if (view.path("revision").path("status").asString().equals("PUBLISHED")) {
            author.post(base + "/regrade", Map.of("reason", "Correction")).expect(200);
        }
        author.put(base + "/answers/" + question("Essay").path("id").asString(),
                Map.of("expectedRevision", revision(base), "points", points, "feedback", "")).expect(200);
        author.post(base + "/finalize", Map.of("expectedRevision", revision(base))).expect(200);
        author.post(base + "/publish", Map.of("expectedRevision", revision(base))).expect(200);
    }

    public JsonNode completion() {
        return learner.get("/me/enrollments/" + enrollmentId + "/completion").expect(200).json();
    }

    private long revision(String base) {
        return author.get(base).json().path("revision").path("version").asLong();
    }

    public JsonNode question(String prompt) {
        for (JsonNode question : exam.path("questions")) {
            if (question.path("prompt").asString().equals(prompt)) {
                return question;
            }
        }
        throw new IllegalArgumentException(prompt);
    }
}
