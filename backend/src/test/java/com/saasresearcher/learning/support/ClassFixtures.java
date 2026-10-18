package com.saasresearcher.learning.support;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Creates classes and enrollments through the public API. */
public final class ClassFixtures {

    private ClassFixtures() {
    }

    public static UUID createClass(ApiClient teacher, UUID courseVersionId, String name, Instant startsAt, Instant endsAt) {
        Map<String, Object> request = new HashMap<>();
        request.put("courseVersionId", courseVersionId);
        request.put("name", name);
        request.put("startsAt", startsAt);
        request.put("endsAt", endsAt);
        return UUID.fromString(teacher.post("/classes", request).expect(201).json().path("id").asString());
    }

    /** Creates and activates a self-paced class. */
    public static UUID activeClass(ApiClient teacher, UUID courseVersionId, String name) {
        UUID classId = createClass(teacher, courseVersionId, name, null, null);
        teacher.post("/classes/" + classId + "/activate", null).expect(200);
        return classId;
    }

    public static UUID enroll(ApiClient teacher, UUID classId, String username) {
        return UUID.fromString(teacher.post("/classes/" + classId + "/enrollments", Map.of("username", username)).expect(201)
                .json().path("enrollmentId").asString());
    }
}
