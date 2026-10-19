package com.saasresearcher.learning.courses.internal;

import com.saasresearcher.learning.courses.api.CourseSection;
import com.saasresearcher.learning.courses.internal.CourseRows.CourseStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response shapes for course authoring endpoints. */
final class CourseViews {

    private CourseViews() {
    }

    record TeacherView(UUID id, String displayName) {
    }

    record EditionView(UUID versionId, int edition, String title, Instant publishedAt) {
    }

    record DraftHeader(UUID versionId, long revision, Integer basedOnEdition) {
    }

    record CourseDetail(UUID id, String title, CourseStatus status, List<TeacherView> teachers, List<EditionView> editions, DraftHeader draft) {
    }

    record DraftView(UUID versionId, UUID courseId, long revision, String title, String description, List<CourseSection> sections,
            List<UUID> requiredExamVersionIds) {
    }

    /** Draft or edition content as a student would see it; {@code edition} is null for drafts. */
    record PreviewView(UUID versionId, UUID courseId, Integer edition, String title, String description, List<CourseSection> sections,
            List<UUID> requiredExamVersionIds) {
    }
}
