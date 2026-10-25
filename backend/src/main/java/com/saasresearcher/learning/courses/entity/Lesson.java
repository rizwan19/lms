package com.saasresearcher.learning.courses.entity;

import com.saasresearcher.learning.courses.api.CourseLesson;
import com.saasresearcher.learning.courses.api.LessonVideo;
import com.saasresearcher.learning.courses.api.VideoProvider;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/** A lesson of an edition; {@code bodyHtml} is stored sanitized. Resources keep their authored order. */
@Getter
@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    private UUID id;

    @Column(name = "course_version_id", nullable = false, updatable = false)
    private UUID courseVersionId;

    @Column(name = "section_id", nullable = false, updatable = false)
    private UUID sectionId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int position;

    @Column(name = "body_html", nullable = false)
    private String bodyHtml;

    @Column(name = "video_asset_id")
    private UUID videoAssetId;

    @Column(name = "video_provider")
    private VideoProvider videoProvider;

    @Column(name = "video_ref")
    private String videoRef;

    @Column(nullable = false)
    private boolean required;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lesson_resources", joinColumns = @JoinColumn(name = "lesson_id"))
    @OrderColumn(name = "position")
    @Column(name = "asset_id", nullable = false)
    private List<UUID> resourceAssetIds = new ArrayList<>();

    protected Lesson() {
    }

    public Lesson(UUID courseVersionId, UUID sectionId, int position, CourseLesson lesson) {
        this.id = lesson.id();
        this.courseVersionId = courseVersionId;
        this.sectionId = sectionId;
        this.position = position;
        this.title = lesson.title();
        this.bodyHtml = lesson.bodyHtml();
        LessonVideo video = lesson.video();
        this.videoAssetId = video == null ? null : video.assetId();
        this.videoProvider = video == null ? null : video.provider();
        this.videoRef = video == null ? null : video.providerRef();
        this.required = lesson.required();
        this.resourceAssetIds = new ArrayList<>(lesson.resourceAssetIds());
    }

    public CourseLesson toLesson() {
        LessonVideo video = videoAssetId == null && videoProvider == null ? null : new LessonVideo(videoAssetId, videoProvider, videoRef);
        return new CourseLesson(id, title, bodyHtml, video, resourceAssetIds, required);
    }
}
