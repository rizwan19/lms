package com.saasresearcher.learning.courses.entity;

import com.saasresearcher.learning.courses.api.CourseOwner;
import com.saasresearcher.learning.courses.api.CourseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** A course with exactly one immutable author. {@code title} follows the latest published edition. */
@Getter
@Entity
@Table(name = "courses")
public class Course {

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private CourseStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    private long version;

    protected Course() {
    }

    public Course(UUID authorId, String title, Instant now) {
        this.id = UUID.randomUUID();
        this.authorId = authorId;
        this.title = title;
        this.status = CourseStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void published(String title, Instant now) {
        this.title = title;
        this.updatedAt = now;
    }

    public void setStatus(CourseStatus status, Instant now) {
        this.status = status;
        this.archivedAt = status == CourseStatus.ARCHIVED ? (archivedAt == null ? now : archivedAt) : null;
        this.updatedAt = now;
    }

    public CourseOwner toOwner() {
        return new CourseOwner(id, authorId, status, title);
    }
}
