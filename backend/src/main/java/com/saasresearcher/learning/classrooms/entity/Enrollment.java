package com.saasresearcher.learning.classrooms.entity;

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
public class Enrollment {

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

  public   Enrollment(UUID classId, UUID studentId, Instant now) {
        this.id = UUID.randomUUID();
        this.classId = classId;
        this.studentId = studentId;
        this.status = EnrollmentStatus.ACTIVE;
        this.enrolledAt = now;
        this.statusChangedAt = now;
    }

    public void changeStatus(EnrollmentStatus status, Instant now) {
        this.status = status;
        this.statusChangedAt = now;
    }

    public void lockLearning() {
        this.learningLocked = true;
    }

    public boolean learningLocked() {
        return learningLocked;
    }

    public EnrollmentView toView(UUID courseVersionId) {
        return new EnrollmentView(id, studentId, classId, courseVersionId, status);
    }

    public UUID id() {
        return id;
    }

    public UUID classId() {
        return classId;
    }

    public UUID studentId() {
        return studentId;
    }

    public EnrollmentStatus status() {
        return status;
    }

    public Instant enrolledAt() {
        return enrolledAt;
    }
}
