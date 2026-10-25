package com.saasresearcher.learning.common;

import java.util.UUID;

/**
 * The authenticated caller, rebuilt from the current account state on every request; never taken from request bodies.
 * Capabilities are global only: owning or learning a course is always checked against the resource itself.
 */
public record Actor(UUID userId, Role role, boolean emailVerified, TeacherStatus teacherStatus) {

    public boolean isTeacher() {
        return role == Role.TEACHER;
    }

    public boolean isApprovedTeacher() {
        return emailVerified && role == Role.TEACHER && teacherStatus == TeacherStatus.APPROVED;
    }

    /** Verified accounts of either role may learn; teaching approval does not change learner checks. */
    public boolean canLearn() {
        return emailVerified;
    }

    public void requireVerified() {
        if (!emailVerified) {
            throw ApiException.emailUnverified();
        }
    }

    /** Verified teachers, approved or not, may author private drafts. */
    public void requireVerifiedTeacher() {
        requireVerified();
        if (role != Role.TEACHER) {
            throw ApiException.accessDenied("A teacher account is required");
        }
    }

    public void requireApprovedTeacher() {
        requireVerifiedTeacher();
        if (teacherStatus != TeacherStatus.APPROVED) {
            throw ApiException.teacherApprovalRequired();
        }
    }
}
