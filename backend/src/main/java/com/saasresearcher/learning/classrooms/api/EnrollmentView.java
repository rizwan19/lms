package com.saasresearcher.learning.classrooms.api;

import java.util.UUID;

/** Shared access result: which class (and therefore which course edition) an enrollment belongs to. */
public record EnrollmentView(UUID id, UUID studentId, UUID classId, UUID courseVersionId, EnrollmentStatus status) {
}
