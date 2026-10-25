package com.saasresearcher.learning.identity.entity;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.common.TeacherStatus;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.identity.api.AccountSummary;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "teacher_status")
    private TeacherStatus teacherStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UserAccount() {
    }

    public UserAccount(String username, String email, String displayName, Role role, String passwordHash, Instant now) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.passwordHash = passwordHash;
        this.status = AccountStatus.ACTIVE;
        this.teacherStatus = role == Role.TEACHER ? TeacherStatus.PENDING : null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void setPassword(String hash, Instant now) {
        this.passwordHash = hash;
        this.updatedAt = now;
    }

    public void verifyEmail(Instant now) {
        if (emailVerifiedAt == null) {
            this.emailVerifiedAt = now;
            this.updatedAt = now;
        }
    }

    public void setTeacherStatus(TeacherStatus teacherStatus, Instant now) {
        this.teacherStatus = teacherStatus;
        this.updatedAt = now;
    }

    public void setStatus(AccountStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isActiveApprovedTeacher() {
        return isActive() && toActor().isApprovedTeacher();
    }

    public Actor toActor() {
        return new Actor(id, role, isEmailVerified(), teacherStatus);
    }

    public AccountSummary toSummary() {
        return new AccountSummary(id, username, displayName, email, role, status, isEmailVerified(), teacherStatus);
    }
}
