package com.saasresearcher.learning.courses.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "sections")
public class Section {

    @Id
    private UUID id;

    @Column(name = "course_version_id", nullable = false, updatable = false)
    private UUID courseVersionId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int position;

    protected Section() {
    }

    public Section(UUID id, UUID courseVersionId, String title, int position) {
        this.id = id;
        this.courseVersionId = courseVersionId;
        this.title = title;
        this.position = position;
    }
}
