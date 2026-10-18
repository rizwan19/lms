package com.saasresearcher.learning.courses.api;

import java.util.List;
import java.util.UUID;

public record CourseSection(UUID id, String title, List<CourseLesson> lessons) {

    public CourseSection {
        lessons = List.copyOf(lessons);
    }
}
