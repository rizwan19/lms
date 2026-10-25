package com.saasresearcher.learning.support;

import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Builds draft payloads and published courses through the public API. */
public final class CourseFixtures {

    public static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);

    private CourseFixtures() {
    }

    public static Map<String, Object> lesson(String title, String bodyHtml, boolean required) {
        Map<String, Object> lesson = new HashMap<>();
        lesson.put("title", title);
        lesson.put("bodyHtml", bodyHtml);
        lesson.put("required", required);
        lesson.put("resourceAssetIds", List.of());
        return lesson;
    }

    public static Map<String, Object> section(String title, List<Map<String, Object>> lessons) {
        return Map.of("title", title, "lessons", lessons);
    }

    public static Map<String, Object> draft(long revision, String title, List<Map<String, Object>> sections) {
        return Map.of("expectedRevision", revision, "title", title, "description", "", "sections", sections);
    }

    public static UUID createCourse(ApiClient author, String title) {
        return UUID.fromString(author.post("/courses", Map.of("title", title)).expect(201).json().path("id").asString());
    }

    public static long draftRevision(ApiClient author, UUID courseId) {
        return author.get("/courses/" + courseId + "/draft").expect(200).json().path("revision").asLong();
    }

    public static JsonNode saveDraft(ApiClient author, UUID courseId, String title, List<Map<String, Object>> sections) {
        return author.put("/courses/" + courseId + "/draft", draft(draftRevision(author, courseId), title, sections)).expect(200).json();
    }

    public static JsonNode publish(ApiClient author, UUID courseId) {
        return author.post("/courses/" + courseId + "/publish", Map.of("expectedRevision", draftRevision(author, courseId))).expect(200).json();
    }

    /** Creates and publishes a course with the given lessons in one section; returns the published edition. */
    public static JsonNode publishedCourse(ApiClient author, String title, List<Map<String, Object>> lessons) {
        UUID courseId = createCourse(author, title);
        saveDraft(author, courseId, title, List.of(section("Unit 1", new ArrayList<>(lessons))));
        return publish(author, courseId);
    }

    public static String uploadPdf(ApiClient author, String filename) {
        ApiResponse response = author.postBytes("/media?purpose=DOCUMENT&filename=" + filename, PDF, "application/pdf").expect(201);
        return response.json().path("id").asString();
    }
}
