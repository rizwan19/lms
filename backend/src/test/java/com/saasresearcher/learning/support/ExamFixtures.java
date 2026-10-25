package com.saasresearcher.learning.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Exam payloads and exams of course drafts through the public API. */
public final class ExamFixtures {

    private ExamFixtures() {
    }

    public static Map<String, Object> option(String label, boolean correct) {
        return Map.of("label", label, "correct", correct);
    }

    public static Map<String, Object> question(String type, String prompt, String points, List<Map<String, Object>> options) {
        Map<String, Object> question = new HashMap<>();
        question.put("type", type);
        question.put("prompt", prompt);
        question.put("points", points);
        question.put("explanation", "Because.");
        question.put("gradingNotes", "");
        question.put("options", options);
        return question;
    }

    public static Map<String, Object> singleChoice(String prompt, String points, int correctIndex) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            options.add(option("Option " + (char) ('A' + i), i == correctIndex));
        }
        return question("SINGLE_CHOICE", prompt, points, options);
    }

    public static Map<String, Object> multipleSelect(String prompt, String points) {
        return question("MULTIPLE_SELECT", prompt, points, List.of(option("Two", true), option("Three", true), option("Four", false)));
    }

    public static Map<String, Object> trueFalse(String prompt, String points, boolean answer) {
        return question("TRUE_FALSE", prompt, points, List.of(option("True", answer), option("False", !answer)));
    }

    public static Map<String, Object> written(String prompt, String points) {
        return question("WRITTEN", prompt, points, List.of());
    }

    /** Adds an exam to the course draft; {@code schedule} overrides defaults (self-paced, 60 minutes, one attempt). */
    public static UUID createExam(ApiClient author, UUID courseId, String title, boolean required, Map<String, Object> schedule) {
        Map<String, Object> request = new HashMap<>(Map.of("title", title, "required", required));
        request.putAll(schedule);
        return UUID.fromString(author.post("/courses/" + courseId + "/exams", request).expect(201).json().path("id").asString());
    }

    public static ApiClient.ApiResponse saveExam(ApiClient author, UUID examId, String title, String passPercent, List<Map<String, Object>> questions) {
        long revision = author.get("/exams/" + examId).expect(200).json().path("revision").asLong();
        return author.put("/exams/" + examId, Map.of("expectedRevision", revision, "title", title, "instructions", "Answer all.",
                "passPercent", passPercent, "questions", questions));
    }

    /** Adds an exam with questions to the course draft; returns its content, keys included. */
    public static JsonNode draftExam(ApiClient author, UUID courseId, String title, String passPercent, List<Map<String, Object>> questions,
            boolean required, Map<String, Object> schedule) {
        UUID examId = createExam(author, courseId, title, required, schedule);
        return saveExam(author, examId, title, passPercent, questions).expect(200).json();
    }
}
