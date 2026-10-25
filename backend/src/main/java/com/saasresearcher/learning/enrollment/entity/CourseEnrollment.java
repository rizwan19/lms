package com.saasresearcher.learning.enrollment.entity;

import com.saasresearcher.learning.enrollment.api.EnrollmentStatus;
import com.saasresearcher.learning.enrollment.api.EnrollmentView;
import com.saasresearcher.learning.enrollment.api.GrantSource;
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
@Table(name = "course_enrollments")
public class CourseEnrollment {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "course_version_id", nullable = false, updatable = false)
    private UUID courseVersionId;

    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;

    @Column(nullable = false)
    private EnrollmentStatus status;

    @Column(name = "grant_source", nullable = false, updatable = false)
    private GrantSource grantSource;

    @Column(name = "granted_by", nullable = false, updatable = false)
    private UUID grantedBy;

    @Column(name = "learning_locked", nullable = false)
    private boolean learningLocked;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Version
    private long version;

    protected CourseEnrollment() {
    }

    public CourseEnrollment(UUID courseId, UUID courseVersionId, UUID learnerId, GrantSource source, UUID grantedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.courseId = courseId;
        this.courseVersionId = courseVersionId;
        this.learnerId = learnerId;
        this.status = EnrollmentStatus.ACTIVE;
        this.grantSource = source;
        this.grantedBy = grantedBy;
        this.grantedAt = now;
        this.statusChangedAt = now;
    }

    public void changeStatus(EnrollmentStatus status, Instant now) {
        this.status = status;
        this.statusChangedAt = now;
    }

    public void lockLearning() {
        this.learningLocked = true;
    }

    public boolean isActive() {
        return status == EnrollmentStatus.ACTIVE;
    }

    public EnrollmentView toView() {
        return new EnrollmentView(id, courseId, courseVersionId, learnerId, status);
    }
}
