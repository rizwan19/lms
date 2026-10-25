package com.saasresearcher.learning.identity.dto;

import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.common.TeacherStatus;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.identity.entity.UserAccount;
import java.time.Instant;
import java.util.UUID;

public final class AccountViews {

    private AccountViews() {
    }

    public record Capabilities(boolean canAdministerWorkspace, boolean canPublishCourses, boolean canLearn) {
    }

    public record ProfileView(UUID id, String username, String displayName, String email, Role role, boolean emailVerified,
                              TeacherStatus teacherStatus, Capabilities capabilities) {

        public static ProfileView of(UserAccount account) {
            boolean approved = account.isActiveApprovedTeacher();
            return new ProfileView(account.getId(), account.getUsername(), account.getDisplayName(), account.getEmail(), account.getRole(),
                    account.isEmailVerified(), account.getTeacherStatus(), new Capabilities(approved, approved, account.isEmailVerified()));
        }
    }

    public record AccountView(UUID id, String username, String displayName, String email, Role role, AccountStatus status, boolean emailVerified,
            TeacherStatus teacherStatus, Instant createdAt, long revision) {

        public static AccountView of(UserAccount account) {
            return new AccountView(account.getId(), account.getUsername(), account.getDisplayName(), account.getEmail(), account.getRole(), account.getStatus(),
                    account.isEmailVerified(), account.getTeacherStatus(), account.getCreatedAt(), account.getVersion());
        }
    }

    public record RegistrationReceipt(UUID userId, String nextAction) {

        public static final String VERIFY_EMAIL = "VERIFY_EMAIL";
    }
}
