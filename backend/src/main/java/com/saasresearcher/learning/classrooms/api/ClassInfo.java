package com.saasresearcher.learning.classrooms.api;

import java.time.Instant;
import java.util.UUID;

public record ClassInfo(UUID id, String name, UUID courseId, UUID courseVersionId, ClassStatus status, Instant startsAt, Instant endsAt) {
}
