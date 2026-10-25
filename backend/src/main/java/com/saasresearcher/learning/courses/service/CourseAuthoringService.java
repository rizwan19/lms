package com.saasresearcher.learning.courses.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.courses.api.CourseEditionListener;
import com.saasresearcher.learning.courses.api.CourseLesson;
import com.saasresearcher.learning.courses.api.CourseSection;
import com.saasresearcher.learning.courses.api.CourseStatus;
import com.saasresearcher.learning.courses.dto.CourseViews.CourseDetail;
import com.saasresearcher.learning.courses.dto.CourseViews.CourseSummary;
import com.saasresearcher.learning.courses.dto.CourseViews.DraftHeader;
import com.saasresearcher.learning.courses.dto.CourseViews.DraftView;
import com.saasresearcher.learning.courses.dto.CourseViews.EditionView;
import com.saasresearcher.learning.courses.dto.CourseViews.PreviewView;
import com.saasresearcher.learning.courses.dto.DraftInput;
import com.saasresearcher.learning.courses.entity.Course;
import com.saasresearcher.learning.courses.entity.CourseVersion;
import com.saasresearcher.learning.courses.api.EditionStatus;
import com.saasresearcher.learning.courses.repository.CourseRepository;
import com.saasresearcher.learning.courses.repository.CourseVersionRepository;
import com.saasresearcher.learning.media.api.MediaService;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course creation and draft editing by the single author. Verified teachers may create and edit private drafts while
 * their approval is pending; publication needs approval. Only the single DRAFT edition is ever changed.
 */
@Service
public class CourseAuthoringService {

    private final CourseRepository courses;
    private final CourseVersionRepository versions;
    private final CourseContent content;
    private final CourseOwnershipServiceServiceImpl ownership;
    private final MediaService media;
    private final ObjectProvider<CourseEditionListener> listeners;
    private final AuditService audit;
    private final Clock clock;

    CourseAuthoringService(CourseRepository courses, CourseVersionRepository versions, CourseContent content, CourseOwnershipServiceServiceImpl ownership,
            MediaService media, ObjectProvider<CourseEditionListener> listeners, AuditService audit, Clock clock) {
        this.courses = courses;
        this.versions = versions;
        this.content = content;
        this.ownership = ownership;
        this.media = media;
        this.listeners = listeners;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public UUID create(String title, Actor actor) {
        actor.requireVerifiedTeacher();
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty() || trimmed.length() > DraftRules.MAX_TITLE) {
            throw ApiException.invalid("title", "Title must be 1-" + DraftRules.MAX_TITLE + " characters");
        }
        Course course = courses.save(new Course(actor.userId(), trimmed, clock.instant()));
        versions.save(new CourseVersion(course.getId(), trimmed, "", null, clock.instant()));
        audit.record(AuditEntry.success(actor.userId(), "course.created", "course", course.getId()));
        return course.getId();
    }

    /** The actor's own courses only. */
    @Transactional(readOnly = true)
    public PageResponse<CourseSummary> list(CourseStatus status, PageQuery page, Actor actor) {
        actor.requireVerifiedTeacher();
        return PageResponse.of(courses.findByAuthorIdAndStatus(actor.userId(), status, page.pageable(Sort.by("title", "id"))), course -> {
            Integer latest = versions.findFirstByCourseIdAndStatusOrderByEditionDesc(course.getId(), EditionStatus.PUBLISHED).map(CourseVersion::getEdition).orElse(null);
            Long draft = versions.findByCourseIdAndStatus(course.getId(), EditionStatus.DRAFT).map(CourseVersion::getVersion).orElse(null);
            return new CourseSummary(course.getId(), course.getTitle(), course.getStatus(), latest, draft, course.getUpdatedAt());
        });
    }

    @Transactional(readOnly = true)
    public CourseDetail detail(UUID courseId, Actor actor) {
        ownership.requireAuthor(courseId, actor);
        Course course = courses.findById(courseId).orElseThrow(ApiException::notFound);
        List<CourseVersion> editions = versions.findByCourseIdAndStatusOrderByEditionDesc(courseId, EditionStatus.PUBLISHED);
        DraftHeader draft = versions.findByCourseIdAndStatus(courseId, EditionStatus.DRAFT)
                .map(row -> new DraftHeader(row.getId(), row.getVersion(), editionOf(row.getBasedOnVersionId(), editions)))
                .orElse(null);
        return new CourseDetail(course.getId(), course.getTitle(), course.getStatus(), course.getAuthorId(),
                editions.stream().map(row -> new EditionView(row.getId(), row.getEdition(), row.getTitle(), row.getPublishedAt())).toList(), draft);
    }

    @Transactional(readOnly = true)
    public DraftView draft(UUID courseId, Actor actor) {
        ownership.requireAuthor(courseId, actor);
        return draftView(requireDraft(courseId));
    }

    @Transactional
    public DraftView saveDraft(UUID courseId, DraftInput input, Actor actor) {
        ownership.requireAuthor(courseId, actor);
        CourseVersion draft = versions.lockDraft(courseId).orElseThrow(CourseAuthoringService::noDraft);
        Revisions.require(input.expectedRevision(), draft.getVersion());
        List<CourseSection> tree = DraftRules.normalize(input, content.sectionIds(draft.getId()), content.lessonIds(draft.getId()));
        requireReadableAssets(tree, actor);
        draft.edit(input.title().trim(), input.description() == null ? "" : input.description().trim(), clock.instant());
        versions.flush();
        content.replaceTree(draft.getId(), tree);
        return draftView(draft);
    }

    /** Starts a correction draft from a published edition; lesson IDs are new, assets are reused. */
    @Transactional
    public DraftView startDraft(UUID courseId, UUID fromVersionId, Actor actor) {
        ownership.requireAuthor(courseId, actor);
        ownership.lock(courseId);
        if (versions.findByCourseIdAndStatus(courseId, EditionStatus.DRAFT).isPresent()) {
            throw ApiException.conflict(CourseErrors.DRAFT_EXISTS, "This course already has a draft");
        }
        CourseVersion source = versions.findById(fromVersionId)
                .filter(version -> !version.isDraft() && version.getCourseId().equals(courseId))
                .orElseThrow(ApiException::notFound);
        CourseVersion draft = versions.saveAndFlush(new CourseVersion(courseId, source.getTitle(), source.getDescription(), source.getId(), clock.instant()));
        content.replaceTree(draft.getId(), withNewIds(content.readTree(source.getId())));
        listeners.forEach(listener -> listener.draftStarted(courseId, source.getId(), draft.getId()));
        audit.record(AuditEntry.success(actor.userId(), "course.draft-started", "course", courseId, Map.of("fromEdition", source.getEdition())));
        return draftView(draft);
    }

    /** Archiving hides the course from the catalog and stops new grants; it never removes access already granted. */
    @Transactional
    public void setStatus(UUID courseId, CourseStatus status, Actor actor) {
        ownership.requirePublishAuthor(courseId, actor);
        ownership.lock(courseId);
        Course course = courses.findById(courseId).orElseThrow();
        course.setStatus(status, clock.instant());
        audit.record(AuditEntry.success(actor.userId(), "course.status-changed", "course", courseId, Map.of("status", status)));
    }

    /** Shows the draft (default when one exists) or any edition exactly as learners would see it. */
    @Transactional(readOnly = true)
    public PreviewView preview(UUID courseId, UUID versionId, Actor actor) {
        ownership.requireAuthor(courseId, actor);
        CourseVersion version = (versionId == null
                ? versions.findByCourseIdAndStatus(courseId, EditionStatus.DRAFT)
                        .or(() -> versions.findFirstByCourseIdAndStatusOrderByEditionDesc(courseId, EditionStatus.PUBLISHED))
                : versions.findById(versionId).filter(row -> row.getCourseId().equals(courseId)))
                .orElseThrow(ApiException::notFound);
        return new PreviewView(version.getId(), courseId, version.getEdition(), version.getTitle(), version.getDescription(), content.readTree(version.getId()));
    }

    private CourseVersion requireDraft(UUID courseId) {
        return versions.findByCourseIdAndStatus(courseId, EditionStatus.DRAFT).orElseThrow(CourseAuthoringService::noDraft);
    }

    private static ApiException noDraft() {
        return ApiException.conflict(CourseErrors.NO_DRAFT, "This course has no draft; start one from an edition");
    }

    private DraftView draftView(CourseVersion draft) {
        return new DraftView(draft.getId(), draft.getCourseId(), draft.getVersion(), draft.getTitle(), draft.getDescription(), content.readTree(draft.getId()));
    }

    /** Every referenced file must be readable by the author, so drafts cannot be used to reach others' files. */
    private void requireReadableAssets(List<CourseSection> tree, Actor actor) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (int s = 0; s < tree.size(); s++) {
            List<CourseLesson> lessons = tree.get(s).lessons();
            for (int l = 0; l < lessons.size(); l++) {
                CourseLesson lesson = lessons.get(l);
                String path = "sections[" + s + "].lessons[" + l + "]";
                Stream.concat(lesson.video() == null || lesson.video().assetId() == null ? Stream.empty()
                                        : Stream.of(Map.entry(path + ".videoAssetId", lesson.video().assetId())),
                                lesson.resourceAssetIds().stream().map(id -> Map.entry(path + ".resourceAssetIds", id)))
                        .filter(entry -> !readable(entry.getValue(), actor))
                        .map(Entry::getKey)
                        .forEach(field -> errors.putIfAbsent(field, "File not found"));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.invalid(errors);
        }
    }

    private boolean readable(UUID assetId, Actor actor) {
        try {
            media.authorizeRead(assetId, actor);
            return true;
        } catch (ApiException notReadable) {
            return false;
        }
    }

    private static List<CourseSection> withNewIds(List<CourseSection> tree) {
        return tree.stream()
                .map(section -> new CourseSection(UUID.randomUUID(), section.title(), section.lessons().stream()
                        .map(lesson -> new CourseLesson(UUID.randomUUID(), lesson.title(), lesson.bodyHtml(), lesson.video(),
                                lesson.resourceAssetIds(), lesson.required()))
                        .toList()))
                .toList();
    }

    private static Integer editionOf(UUID versionId, List<CourseVersion> editions) {
        return Optional.ofNullable(versionId)
                .flatMap(id -> editions.stream().filter(row -> row.getId().equals(id)).map(CourseVersion::getEdition).findFirst())
                .orElse(null);
    }
}
