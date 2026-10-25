package com.saasresearcher.learning.courses.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.courses.api.CourseCatalogService;
import com.saasresearcher.learning.courses.api.CourseEditionListener;
import com.saasresearcher.learning.courses.api.CourseLesson;
import com.saasresearcher.learning.courses.api.CourseSection;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.courses.entity.Course;
import com.saasresearcher.learning.courses.entity.CourseVersion;
import com.saasresearcher.learning.courses.repository.CourseRepository;
import com.saasresearcher.learning.courses.repository.CourseVersionRepository;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.media.api.AssetPurpose;
import com.saasresearcher.learning.media.api.MediaCatalog;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seals the draft as the next immutable edition. The author's approval is rechecked under a shared account lock, then
 * the course row is locked, so concurrent publishes cannot both win and a suspension cannot slip in between.
 */
@Service
public class CoursePublicationService {

    private final CourseRepository courses;
    private final CourseVersionRepository versions;
    private final CourseContent content;
    private final CourseOwnershipServiceServiceImpl ownership;
    private final IdentityAccess identity;
    private final MediaCatalog media;
    private final CourseCatalogService catalog;
    private final ObjectProvider<CourseEditionListener> listeners;
    private final AuditService audit;
    private final Clock clock;

    CoursePublicationService(CourseRepository courses, CourseVersionRepository versions, CourseContent content, CourseOwnershipServiceServiceImpl ownership,
                             IdentityAccess identity, MediaCatalog media, CourseCatalogService catalog, ObjectProvider<CourseEditionListener> listeners, AuditService audit,
                             Clock clock) {
        this.courses = courses;
        this.versions = versions;
        this.content = content;
        this.ownership = ownership;
        this.identity = identity;
        this.media = media;
        this.catalog = catalog;
        this.listeners = listeners;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PublishedCourse publish(UUID courseId, long expectedRevision, Actor actor) {
        ownership.requirePublishAuthor(courseId, actor);
        AccountSummary author = identity.lockShared(List.of(actor.userId())).get(actor.userId());
        if (author == null || !author.isActiveApprovedTeacher()) {
            throw ApiException.teacherApprovalRequired();
        }
        ownership.lock(courseId);
        CourseVersion draft = versions.lockDraft(courseId)
                .orElseThrow(() -> ApiException.conflict(CourseErrors.NO_DRAFT, "There is no draft to publish"));
        Revisions.require(expectedRevision, draft.getVersion());
        requirePublishable(draft, content.readTree(draft.getId()));
        listeners.forEach(listener -> listener.beforePublish(courseId, draft.getId()));

        int edition = versions.nextEdition(courseId);
        draft.publish(edition, clock.instant());
        Course course = courses.findById(courseId).orElseThrow();
        course.published(draft.getTitle(), clock.instant());
        versions.flush();
        audit.record(AuditEntry.success(actor.userId(), "course.published", "course", courseId, Map.of("edition", edition)));
        return catalog.requirePublished(draft.getId());
    }

    private void requirePublishable(CourseVersion draft, List<CourseSection> tree) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (draft.getTitle().isBlank()) {
            errors.put("title", "Required");
        }
        if (tree.stream().allMatch(section -> section.lessons().isEmpty())) {
            errors.put("sections", "Add at least one lesson before publishing");
        }
        for (int s = 0; s < tree.size(); s++) {
            List<CourseLesson> lessons = tree.get(s).lessons();
            for (int l = 0; l < lessons.size(); l++) {
                CourseLesson lesson = lessons.get(l);
                String path = "sections[" + s + "].lessons[" + l + "]";
                if (lesson.video() != null && lesson.video().assetId() != null) {
                    requireAsset(lesson.video().assetId(), AssetPurpose.VIDEO, path + ".videoAssetId", errors);
                }
                lesson.resourceAssetIds().forEach(id -> requireAsset(id, AssetPurpose.DOCUMENT, path + ".resourceAssetIds", errors));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.invalid(errors);
        }
    }

    private void requireAsset(UUID assetId, AssetPurpose purpose, String field, Map<String, String> errors) {
        try {
            if (media.requireAvailable(assetId).purpose() != purpose) {
                errors.putIfAbsent(field, purpose == AssetPurpose.VIDEO ? "Choose an uploaded video" : "Choose an uploaded document");
            }
        } catch (ApiException unavailable) {
            errors.putIfAbsent(field, "The file is missing or not available");
        }
    }
}
