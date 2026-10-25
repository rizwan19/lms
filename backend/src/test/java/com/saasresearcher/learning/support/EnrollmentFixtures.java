package com.saasresearcher.learning.support;

import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Grants course access through the public request/approval flow. */
public final class EnrollmentFixtures {

    private EnrollmentFixtures() {
    }

    /** The learner requests access and the author approves; returns the new enrollment ID. */
    public static UUID enroll(ApiClient author, ApiClient learner, UUID courseId) {
        JsonNode request = learner.post("/courses/" + courseId + "/access-requests", null).expect(200).json();
        author.post("/access-requests/" + request.path("id").asString() + "/decision",
                Map.of("expectedRevision", request.path("revision").asLong(), "approve", true)).expect(200).json();
        return enrollmentOf(learner, courseId);
    }

    public static UUID enrollmentOf(ApiClient learner, UUID courseId) {
        return learner.get("/me/enrollments").expect(200).json().valueStream()
                .filter(entry -> entry.path("courseId").asString().equals(courseId.toString()))
                .map(entry -> UUID.fromString(entry.path("enrollmentId").asString()))
                .findFirst().orElseThrow(() -> new AssertionError("No enrollment in " + courseId));
    }
}
