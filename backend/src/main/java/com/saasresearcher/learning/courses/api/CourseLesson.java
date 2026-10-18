package com.saasresearcher.learning.courses.api;

import java.util.List;
import java.util.UUID;

/** Lesson content of an edition; {@code bodyHtml} is already sanitized. {@code video} may be null. */
public record CourseLesson(UUID id, String title, String bodyHtml, LessonVideo video, List<UUID> resourceAssetIds, boolean required) {

    public CourseLesson {
        resourceAssetIds = List.copyOf(resourceAssetIds);
    }
}
