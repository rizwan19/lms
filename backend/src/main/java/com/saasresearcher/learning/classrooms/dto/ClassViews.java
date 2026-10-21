package com.saasresearcher.learning.classrooms.dto;

import com.saasresearcher.learning.classrooms.api.ClassStatus;
import com.saasresearcher.learning.classrooms.api.EnrollmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response shapes for class and roster endpoints. */
public final class ClassViews {

    private ClassViews() {
    }

    public record ClassSummary(UUID id, String name, ClassStatus status, String courseTitle, int edition, Instant startsAt, Instant endsAt) {
    }

    public record TeacherView(UUID id, String displayName) {
    }

    public record ClassDetail(UUID id, String name, ClassStatus status, UUID courseId, UUID courseVersionId, String courseTitle, int edition,
            Instant startsAt, Instant endsAt, List<TeacherView> teachers, long revision) {
    }

    public record RosterRow(UUID enrollmentId, UUID studentId, String username, String displayName, EnrollmentStatus status, Instant enrolledAt) {
    }
}
