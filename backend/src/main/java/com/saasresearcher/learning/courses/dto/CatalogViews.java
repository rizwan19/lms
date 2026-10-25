package com.saasresearcher.learning.courses.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public catalog shapes. Deliberately limited to summary metadata: never lesson bodies, video or file references,
 * learner identities, exams or anything from an unpublished draft.
 */
public final class CatalogViews {

    private CatalogViews() {
    }

    public record EditionSummary(int edition, Instant publishedAt, int sectionCount, int lessonCount) {
    }

    public record CatalogCourse(UUID id, String title, String summary, String authorName, EditionSummary edition) {
    }

    /** The outline lists section and lesson titles only. */
    public record CatalogCourseDetail(UUID id, String title, String description, String authorName, EditionSummary edition,
            List<OutlineSection> outline) {
    }

    public record OutlineSection(String title, List<String> lessons) {
    }
}
