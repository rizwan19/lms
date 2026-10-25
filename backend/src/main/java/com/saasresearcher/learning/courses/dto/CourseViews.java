package com.saasresearcher.learning.courses.dto;

import com.saasresearcher.learning.courses.api.CourseSection;
import com.saasresearcher.learning.courses.api.CourseStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response shapes for the author's course endpoints. */
public final class CourseViews {

    private CourseViews() {
    }

    public record CourseSummary(UUID id, String title, CourseStatus status, Integer latestEdition, Long draftRevision, Instant updatedAt) {
    }

    public record EditionView(UUID versionId, int edition, String title, Instant publishedAt) {
    }

    public record DraftHeader(UUID versionId, long revision, Integer basedOnEdition) {
    }

    public record CourseDetail(UUID id, String title, CourseStatus status, UUID authorId, List<EditionView> editions, DraftHeader draft) {
    }

    public record DraftView(UUID versionId, UUID courseId, long revision, String title, String description, List<CourseSection> sections) {
    }

    /** Draft or edition content as a learner would see it; {@code edition} is null for drafts. */
    public record PreviewView(UUID versionId, UUID courseId, Integer edition, String title, String description, List<CourseSection> sections) {
    }
}
