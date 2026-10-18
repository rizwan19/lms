package com.saasresearcher.learning.classrooms.api;

import java.time.Instant;

/** A student's own enrollment with the class context needed for dashboards, including historical ones. */
public record StudentEnrollment(EnrollmentView enrollment, String className, ClassStatus classStatus, Instant startsAt, Instant endsAt,
        Instant enrolledAt, boolean accessOpen) {
}
