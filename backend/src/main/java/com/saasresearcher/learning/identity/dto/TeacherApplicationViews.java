package com.saasresearcher.learning.identity.dto;

import com.saasresearcher.learning.identity.api.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

public final class TeacherApplicationViews {

    private TeacherApplicationViews() {
    }

    public record ApplicationView(UUID id, UUID applicantId, String username, String displayName, String email, ApplicationStatus status, String reason,
            Instant submittedAt, Instant decidedAt, long revision) {
    }

    public record Decision(long expectedRevision, boolean approve, String reason) {
    }
}
