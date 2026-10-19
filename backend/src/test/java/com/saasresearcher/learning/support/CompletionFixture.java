package com.saasresearcher.learning.support;

import static com.saasresearcher.learning.support.ClassFixtures.activeClass;
import static com.saasresearcher.learning.support.ClassFixtures.enroll;
import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.draft;
import static com.saasresearcher.learning.support.CourseFixtures.draftRevision;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.ExamFixtures.publishedExam;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.written;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * A class whose edition requires two lessons (a third is optional) and one mixed exam (1-point single choice plus a
 * 4-point essay, pass mark 50%). The student is enrolled; helpers drive study, attempts and grading through the API.
 */
public record CompletionFixture(ApiClient teacher, ApiClient student, UUID classId, UUID enrollmentId, List<String> lessonIds, UUID assignmentId,
        JsonNode exam) {

    public static CompletionFixture create(ApiClient teacher, ApiClient student, String studentUsername, int maxAttempts) {
        UUID courseId = createCourse(teacher, "Completion course " + UUID.randomUUID());
        JsonNode exam = publishedExam(teacher, courseId, "Final", "50", List.of(singleChoice("Pick A", "1", 0), written("Essay", "4")));
        List<Map<String, Object>> lessons = List.of(lesson("One", "", true), lesson("Two", "", true), lesson("Extra", "", false));
        teacher.put("/courses/" + courseId + "/draft", draft(draftRevision(teacher, courseId), "Completion course", List.of(section("S", lessons)),
                List.of(exam.path("versionId").asString()))).expect(200);
        JsonNode edition = publish(teacher, courseId);
        UUID classId = activeClass(teacher, UUID.fromString(edition.path("versionId").asString()), "Completion class");
        UUID enrollmentId = enroll(teacher, classId, studentUsername);
        JsonNode assignment = teacher.get("/exam-assignments?classId=" + classId).json().get(0);
        UUID assignmentId = UUID.fromString(assignment.path("id").asString());
        teacher.put("/exam-assignments/" + assignmentId, Map.of("expectedRevision", assignment.path("revision").asLong(), "durationMinutes", 60,
                "maxAttempts", maxAttempts)).expect(200);
        return new CompletionFixture(teacher, student, classId, enrollmentId,
                edition.path("sections").get(0).path("lessons").findValuesAsString("id"), assignmentId, exam);
    }

    public void completeLesson(int index, boolean completed) {
        String path = "/enrollments/" + enrollmentId + "/lessons/" + lessonIds.get(index) + "/progress";
        long revision = student.get("/me/enrollments/" + enrollmentId).json().path("progress").findParents("lessonId").stream()
                .filter(node -> node.path("lessonId").asString().equals(lessonIds.get(index)))
                .map(node -> node.path("revision").asLong()).findFirst().orElse(0L);
        student.put(path, Map.of("expectedRevision", revision, "completed", completed, "resumeSeconds", 0)).expect(200);
    }

    public ApiClient.ApiResponse completeLessonResponse(int index, boolean completed, long revision) {
        return student.put("/enrollments/" + enrollmentId + "/lessons/" + lessonIds.get(index) + "/progress",
                Map.of("expectedRevision", revision, "completed", completed, "resumeSeconds", 0));
    }

    /** Submits an attempt with the single-choice answer correct; returns the attempt ID (essay ungraded). */
    public String submitAttempt() {
        String attemptId = student.post("/exam-assignments/" + assignmentId + "/attempts", null).expect(200).json().path("id").asString();
        JsonNode single = question("Pick A");
        student.put("/attempts/" + attemptId + "/answers/" + single.path("id").asString(),
                Map.of("expectedRevision", 0, "selectedOptionIds", List.of(single.path("options").get(0).path("id").asString()))).expect(200);
        student.post("/attempts/" + attemptId + "/submit", null).expect(200);
        return attemptId;
    }

    /** Grades the essay, finalizes and publishes (opening a regrade first when a result is already published). */
    public void publishEssayGrade(String attemptId, String points) {
        String base = "/grading/attempts/" + attemptId;
        JsonNode view = teacher.get(base).expect(200).json();
        if (view.path("revision").path("status").asString().equals("PUBLISHED")) {
            teacher.post(base + "/regrade", Map.of("reason", "Correction")).expect(200);
        }
        teacher.put(base + "/answers/" + question("Essay").path("id").asString(),
                Map.of("expectedRevision", revision(base), "points", points, "feedback", "")).expect(200);
        teacher.post(base + "/finalize", Map.of("expectedRevision", revision(base))).expect(200);
        teacher.post(base + "/publish", Map.of("expectedRevision", revision(base))).expect(200);
    }

    public JsonNode completion() {
        return student.get("/me/enrollments/" + enrollmentId + "/completion").expect(200).json();
    }

    private long revision(String base) {
        return teacher.get(base).json().path("revision").path("version").asLong();
    }

    private JsonNode question(String prompt) {
        for (JsonNode question : exam.path("questions")) {
            if (question.path("prompt").asString().equals(prompt)) {
                return question;
            }
        }
        throw new IllegalArgumentException(prompt);
    }
}
