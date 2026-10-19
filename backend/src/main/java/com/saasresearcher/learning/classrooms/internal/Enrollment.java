package com.saasresearcher.learning.classrooms.internal;

import com.saasresearcher.learning.classrooms.api.EnrollmentStatus;
import com.saasresearcher.learning.classrooms.api.EnrollmentView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollments")
class Enrollment {

    @Id
    private UUID id;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "learning_locked", nullable = false)
    private boolean learningLocked;

    @Version
    private long version;

    protected Enrollment() {
    }

    Enrollment(UUID classId, UUID studentId, Instant now) {
        this.id = UUID.randomUUID();
        this.classId = classId;
        this.studentId = studentId;
        this.status = EnrollmentStatus.ACTIVE;
        this.enrolledAt = now;
        this.statusChangedAt = now;
    }

    void changeStatus(EnrollmentStatus status, Instant now) {
        this.status = status;
        this.statusChangedAt = now;
    }

    void lockLearning() {
        this.learningLocked = true;
    }

    boolean learningLocked() {
        return learningLocked;
    }

    EnrollmentView toView(UUID courseVersionId) {
        return new EnrollmentView(id, studentId, classId, courseVersionId, status);
    }

    UUID id() {
        return id;
    }

    UUID classId() {
        return classId;
    }

    UUID studentId() {
        return studentId;
    }

    EnrollmentStatus status() {
        return status;
    }

    Instant enrolledAt() {
        return enrolledAt;
    }
}
