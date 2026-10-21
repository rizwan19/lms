package com.saasresearcher.learning.courses.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.courses.api.CourseCatalog;
import com.saasresearcher.learning.courses.api.CourseLesson;
import com.saasresearcher.learning.courses.api.CourseSection;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.courses.entity.CourseRows.CourseRow;
import com.saasresearcher.learning.courses.entity.CourseRows.VersionRow;
import com.saasresearcher.learning.courses.repository.CourseContentRepository;
import com.saasresearcher.learning.courses.repository.CourseRepository;
import com.saasresearcher.learning.media.api.AssetPurpose;
import com.saasresearcher.learning.media.api.MediaCatalog;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Seals the draft as the next immutable edition under the course row lock, so concurrent publishes cannot both win. */
@Service
public class CoursePublicationService {

    private final CourseRepository courses;
    private final CourseContentRepository content;
    private final CourseAccess access;
    private final MediaCatalog media;
    private final CourseCatalog catalog;
    private final RequiredExams requiredExams;
    private final AuditLog audit;
    private final Clock clock;

    CoursePublicationService(CourseRepository courses, CourseContentRepository content, CourseAccess access, MediaCatalog media,
            CourseCatalog catalog, RequiredExams requiredExams, AuditLog audit, Clock clock) {
        this.courses = courses;
        this.content = content;
        this.access = access;
        this.media = media;
        this.catalog = catalog;
        this.requiredExams = requiredExams;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PublishedCourse publish(UUID courseId, long expectedRevision, Actor actor) {
        CourseRow course = access.lockCourse(courseId, actor);
        VersionRow draft = courses.lockDraft(courseId)
                .orElseThrow(() -> ApiException.conflict(CourseErrors.NO_DRAFT, "There is no draft to publish"));
        Revisions.require(expectedRevision, draft.version());
        requirePublishable(draft, content.readTree(draft.id()));
        requiredExams.require(courseId, content.requiredExams(draft.id()));

        int edition = courses.nextEdition(courseId);
        courses.publish(draft.id(), edition, actor.userId(), clock.instant());
        courses.updateCourse(courseId, draft.title(), course.status(), clock.instant());
        audit.record(AuditEntry.success(actor.userId(), "course.published", "course", courseId, Map.of("edition", edition)));
        return catalog.requirePublished(draft.id());
    }

    private void requirePublishable(VersionRow draft, List<CourseSection> tree) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (draft.title().isBlank()) {
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
