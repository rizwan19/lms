package com.saasresearcher.learning.identity.api;

import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.common.TeacherStatus;
import java.util.UUID;

public record AccountSummary(UUID id, String username, String displayName, String email, Role role, AccountStatus status,
        boolean emailVerified, TeacherStatus teacherStatus) {

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isActiveApprovedTeacher() {
        return isActive() && emailVerified && role == Role.TEACHER && teacherStatus == TeacherStatus.APPROVED;
    }
}
