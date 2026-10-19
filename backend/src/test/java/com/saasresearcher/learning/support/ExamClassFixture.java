package com.saasresearcher.learning.support;

import static com.saasresearcher.learning.support.ClassFixtures.activeClass;
import static com.saasresearcher.learning.support.ClassFixtures.enroll;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static com.saasresearcher.learning.support.ExamFixtures.publishedExam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** A course with one active class, an enrolled student and a published exam ready to schedule. */
public record ExamClassFixture(UUID courseId, UUID classId, UUID enrollmentId, JsonNode exam) {

    public static ExamClassFixture create(ApiClient teacher, String studentUsername, List<Map<String, Object>> questions) {
        JsonNode course = publishedCourse(teacher, "Science " + UUID.randomUUID(), List.of(lesson("Intro", "", true)));
        UUID courseId = UUID.fromString(course.path("courseId").asString());
        UUID classId = activeClass(teacher, UUID.fromString(course.path("versionId").asString()), "Science A");
        UUID enrollmentId = enroll(teacher, classId, studentUsername);
        return new ExamClassFixture(courseId, classId, enrollmentId, publishedExam(teacher, courseId, "Quiz", "50", questions));
    }

    /** Schedules this fixture's exam; {@code schedule} overrides defaults (60 minutes, one attempt). */
    public UUID assign(ApiClient teacher, Map<String, Object> schedule) {
        Map<String, Object> request = new HashMap<>(Map.of("classId", classId, "examVersionId", exam.path("versionId").asString(),
                "durationMinutes", 60, "maxAttempts", 1));
        request.putAll(schedule);
        return UUID.fromString(teacher.post("/exam-assignments", request).expect(201).json().path("id").asString());
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

    public record QuestionKey(UUID questionId, List<String> optionIds) {
    }
}
