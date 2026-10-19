package com.saasresearcher.learning.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Exam payloads and published exams through the public API. */
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

    public static UUID createExam(ApiClient author, UUID courseId, String title) {
        return UUID.fromString(author.post("/exams", Map.of("courseId", courseId, "title", title)).expect(201).json().path("id").asString());
    }

    public static ApiClient.ApiResponse saveExam(ApiClient author, UUID examId, String title, String passPercent, List<Map<String, Object>> questions) {
        long revision = author.get("/exams/" + examId + "/draft").expect(200).json().path("revision").asLong();
        return author.put("/exams/" + examId + "/draft", Map.of("expectedRevision", revision, "title", title, "instructions", "Answer all.",
                "passPercent", passPercent, "questions", questions));
    }

    public static JsonNode publishExam(ApiClient author, UUID examId) {
        long revision = author.get("/exams/" + examId + "/draft").expect(200).json().path("revision").asLong();
        return author.post("/exams/" + examId + "/publish", Map.of("expectedRevision", revision)).expect(200).json();
    }

    /** Creates and publishes an exam; returns the published edition content. */
    public static JsonNode publishedExam(ApiClient author, UUID courseId, String title, String passPercent, List<Map<String, Object>> questions) {
        UUID examId = createExam(author, courseId, title);
        saveExam(author, examId, title, passPercent, questions).expect(200);
        return publishExam(author, examId);
    }
}
