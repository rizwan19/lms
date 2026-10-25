package com.saasresearcher.learning.enrollment.api;

import java.util.UUID;

public record EnrollmentView(UUID id, UUID courseId, UUID courseVersionId, UUID learnerId, EnrollmentStatus status) {
}
