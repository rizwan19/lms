package com.saasresearcher.learning.courses.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** An immutable published edition. {@code archived} courses keep serving existing classes but accept no new ones. */
public record PublishedCourse(UUID versionId, UUID courseId, int edition, String title, String description, Instant publishedAt,
        boolean archived, List<CourseSection> sections) {

    public PublishedCourse {
        sections = List.copyOf(sections);
    }

    public Set<UUID> requiredLessonIds() {
        return lessons().filter(CourseLesson::required).map(CourseLesson::id).collect(Collectors.toUnmodifiableSet());
    }

    public Optional<CourseLesson> lesson(UUID lessonId) {
        return lessons().filter(lesson -> lesson.id().equals(lessonId)).findFirst();
    }

    public Stream<CourseLesson> lessons() {
        return sections.stream().flatMap(section -> section.lessons().stream());
    }
}
