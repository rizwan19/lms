package com.saasresearcher.learning.courses.api;

import java.util.UUID;

/** A course's single, immutable author and its lifecycle state. */
public record CourseOwner(UUID courseId, UUID authorId, CourseStatus status, String title) {

    public boolean isArchived() {
        return status == CourseStatus.ARCHIVED;
    }
}
