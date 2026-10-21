package com.saasresearcher.learning.courses.entity;

import java.time.Instant;
import java.util.UUID;

/** Row shapes read by the course repositories. */
public final class CourseRows {

    private CourseRows() {
    }

    public enum CourseStatus { ACTIVE, ARCHIVED }

    public enum VersionStatus { DRAFT, PUBLISHED }

    public record CourseRow(UUID id, String title, CourseStatus status, UUID createdBy, Instant updatedAt, long version) {
    }

    public record VersionRow(UUID id, UUID courseId, Integer edition, VersionStatus status, String title, String description,
            UUID basedOnVersionId, Instant publishedAt, long version) {

        public boolean isDraft() {
            return status == VersionStatus.DRAFT;
        }
    }

    public record CourseSummary(UUID id, String title, CourseStatus status, Integer latestEdition, Long draftRevision, Instant updatedAt) {
    }
}
