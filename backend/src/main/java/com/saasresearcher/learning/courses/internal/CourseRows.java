package com.saasresearcher.learning.courses.internal;

import java.time.Instant;
import java.util.UUID;

/** Row shapes read by the course repositories. */
final class CourseRows {

    private CourseRows() {
    }

    enum CourseStatus { ACTIVE, ARCHIVED }

    enum VersionStatus { DRAFT, PUBLISHED }

    record CourseRow(UUID id, String title, CourseStatus status, UUID createdBy, Instant updatedAt, long version) {
    }

    record VersionRow(UUID id, UUID courseId, Integer edition, VersionStatus status, String title, String description,
            UUID basedOnVersionId, Instant publishedAt, long version) {

        boolean isDraft() {
            return status == VersionStatus.DRAFT;
        }
    }

    record CourseSummary(UUID id, String title, CourseStatus status, Integer latestEdition, Long draftRevision, Instant updatedAt) {
    }
}
