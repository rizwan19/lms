package com.saasresearcher.learning.classrooms.internal;

import com.saasresearcher.learning.classrooms.api.ClassInfo;
import com.saasresearcher.learning.classrooms.api.ClassStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "classes")
class Classroom {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "course_version_id", nullable = false)
    private UUID courseVersionId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassStatus status;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "class_teachers", joinColumns = @JoinColumn(name = "class_id"))
    @Column(name = "teacher_id")
    private Set<UUID> teacherIds = new HashSet<>();

    protected Classroom() {
    }

    Classroom(UUID courseId, UUID courseVersionId, String name, Instant startsAt, Instant endsAt, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.courseId = courseId;
        this.courseVersionId = courseVersionId;
        this.status = ClassStatus.DRAFT;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.teacherIds.add(createdBy);
        reschedule(name, startsAt, endsAt, now);
    }

    void reschedule(String name, Instant startsAt, Instant endsAt, Instant now) {
        this.name = name;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.updatedAt = now;
    }

    void activate(Instant now) {
        this.status = ClassStatus.ACTIVE;
        this.activatedAt = now;
        this.updatedAt = now;
    }

    void archive(Instant now) {
        this.status = ClassStatus.ARCHIVED;
        this.archivedAt = now;
        this.updatedAt = now;
    }

    void replaceTeachers(Collection<UUID> teachers, Instant now) {
        teacherIds.clear();
        teacherIds.addAll(teachers);
        this.updatedAt = now;
    }

    /** Learning access needs an active class inside its optional date window. */
    boolean isOpen(Instant now) {
        return status == ClassStatus.ACTIVE && (startsAt == null || !now.isBefore(startsAt)) && (endsAt == null || now.isBefore(endsAt));
    }

    ClassInfo toInfo() {
        return new ClassInfo(id, name, courseId, courseVersionId, status, startsAt, endsAt);
    }

    boolean hasTeacher(UUID userId) {
        return teacherIds.contains(userId);
    }

    UUID id() {
        return id;
    }

    UUID courseId() {
        return courseId;
    }

    UUID courseVersionId() {
        return courseVersionId;
    }

    String name() {
        return name;
    }

    ClassStatus status() {
        return status;
    }

    Instant startsAt() {
        return startsAt;
    }

    Instant endsAt() {
        return endsAt;
    }

    Set<UUID> teacherIds() {
        return Set.copyOf(teacherIds);
    }

    long version() {
        return version;
    }
}
