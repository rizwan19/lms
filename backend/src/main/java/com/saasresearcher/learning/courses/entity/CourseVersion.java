package com.saasresearcher.learning.courses.entity;

import com.saasresearcher.learning.courses.api.EditionStatus;
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
@Table(name = "course_versions")
public class CourseVersion {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    private Integer edition;

    @Column(nullable = false)
    private EditionStatus status;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "based_on_version_id", updatable = false)
    private UUID basedOnVersionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    private long version;

    protected CourseVersion() {
    }

    public CourseVersion(UUID courseId, String title, String description, UUID basedOnVersionId, Instant now) {
        this.id = UUID.randomUUID();
        this.courseId = courseId;
        this.status = EditionStatus.DRAFT;
        this.title = title;
        this.description = description;
        this.basedOnVersionId = basedOnVersionId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void edit(String title, String description, Instant now) {
        this.title = title;
        this.description = description;
        this.updatedAt = now;
    }

    public void publish(int edition, Instant now) {
        this.status = EditionStatus.PUBLISHED;
        this.edition = edition;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public boolean isDraft() {
        return status == EditionStatus.DRAFT;
    }
}
