package com.saasresearcher.learning.classrooms.internal;

import com.saasresearcher.learning.classrooms.api.ClassStatus;
import com.saasresearcher.learning.classrooms.api.EnrollmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response shapes for class and roster endpoints. */
final class ClassViews {

    private ClassViews() {
    }

    record ClassSummary(UUID id, String name, ClassStatus status, String courseTitle, int edition, Instant startsAt, Instant endsAt) {
    }

    record TeacherView(UUID id, String displayName) {
    }

    record ClassDetail(UUID id, String name, ClassStatus status, UUID courseId, UUID courseVersionId, String courseTitle, int edition,
            Instant startsAt, Instant endsAt, List<TeacherView> teachers, long revision) {
    }

    record RosterRow(UUID enrollmentId, UUID studentId, String username, String displayName, EnrollmentStatus status, Instant enrolledAt) {
    }
}
