package com.saasresearcher.learning.courses.internal;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.ErrorCodes;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.courses.api.CourseLesson;
import com.saasresearcher.learning.courses.api.CourseSection;
import com.saasresearcher.learning.courses.internal.CourseRows.CourseRow;
import com.saasresearcher.learning.courses.internal.CourseRows.CourseStatus;
import com.saasresearcher.learning.courses.internal.CourseRows.CourseSummary;
import com.saasresearcher.learning.courses.internal.CourseRows.VersionRow;
import com.saasresearcher.learning.courses.internal.CourseViews.CourseDetail;
import com.saasresearcher.learning.courses.internal.CourseViews.DraftHeader;
import com.saasresearcher.learning.courses.internal.CourseViews.DraftView;
import com.saasresearcher.learning.courses.internal.CourseViews.EditionView;
import com.saasresearcher.learning.courses.internal.CourseViews.PreviewView;
import com.saasresearcher.learning.courses.internal.CourseViews.TeacherView;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.media.api.MediaService;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Course identity, teacher assignment and draft editing. Only the single DRAFT edition is ever changed. */
@Service
class CourseAuthoringService {

    private final CourseRepository courses;
    private final CourseContentRepository content;
    private final CourseAccess access;
    private final MediaService media;
    private final IdentityAccess identity;
    private final RequiredExams requiredExams;
    private final AuditLog audit;
    private final Clock clock;

    CourseAuthoringService(CourseRepository courses, CourseContentRepository content, CourseAccess access, MediaService media,
            IdentityAccess identity, RequiredExams requiredExams, AuditLog audit, Clock clock) {
        this.courses = courses;
        this.content = content;
        this.access = access;
        this.media = media;
        this.identity = identity;
        this.requiredExams = requiredExams;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    UUID create(String title, Actor actor) {
        access.requireAuthorRole(actor);
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty() || trimmed.length() > DraftRules.MAX_TITLE) {
            throw ApiException.invalid("title", "Title must be 1-" + DraftRules.MAX_TITLE + " characters");
        }
        UUID courseId = UUID.randomUUID();
        courses.insertCourse(courseId, trimmed, actor.userId(), clock.instant());
        courses.replaceTeachers(courseId, List.of(actor.userId()));
        courses.insertDraft(UUID.randomUUID(), courseId, trimmed, "", null, clock.instant());
        audit.record(AuditEntry.success(actor.userId(), "course.created", "course", courseId));
        return courseId;
    }

    @Transactional(readOnly = true)
    PageResponse<CourseSummary> list(CourseStatus status, PageQuery page, Actor actor) {
        access.requireAuthorRole(actor);
        UUID teacher = actor.isAdministrator() ? null : actor.userId();
        return PageResponse.of(courses.listCourses(teacher, status, page), page, courses.countCourses(teacher, status));
    }

    @Transactional(readOnly = true)
    CourseDetail detail(UUID courseId, Actor actor) {
        CourseRow course = access.requireCourse(courseId, actor);
        List<VersionRow> editions = courses.editions(courseId);
        DraftHeader draft = courses.findDraft(courseId)
                .map(row -> new DraftHeader(row.id(), row.version(), editionOf(row.basedOnVersionId(), editions)))
                .orElse(null);
        List<TeacherView> teachers = identity.summaries(courses.teachers(courseId)).values().stream()
                .map(summary -> new TeacherView(summary.id(), summary.displayName()))
                .sorted(Comparator.comparing(TeacherView::displayName))
                .toList();
        return new CourseDetail(course.id(), course.title(), course.status(), teachers,
                editions.stream().map(row -> new EditionView(row.id(), row.edition(), row.title(), row.publishedAt())).toList(), draft);
    }

    @Transactional(readOnly = true)
    DraftView draft(UUID courseId, Actor actor) {
        access.requireCourse(courseId, actor);
        return draftView(requireDraft(courseId));
    }

    @Transactional
    DraftView saveDraft(UUID courseId, DraftInput input, Actor actor) {
        access.requireCourse(courseId, actor);
        VersionRow draft = requireDraft(courseId);
        List<CourseSection> tree = DraftRules.normalize(input, content.sectionIds(draft.id()), content.lessonIds(draft.id()));
        requireReadableAssets(tree, actor);
        List<UUID> examIds = input.requiredExamVersionIds() == null ? List.of() : List.copyOf(new LinkedHashSet<>(input.requiredExamVersionIds()));
        requiredExams.require(draft.courseId(), examIds);
        if (!courses.updateDraft(draft.id(), input.expectedRevision(), input.title().trim(),
                input.description() == null ? "" : input.description().trim(), clock.instant())) {
            throw ApiException.conflict(ErrorCodes.REVISION_CONFLICT,
                    "The draft was changed by another request; reload and retry");
        }
        content.replaceTree(draft.id(), tree);
        content.replaceRequiredExams(draft.id(), examIds);
        return draftView(requireDraft(courseId));
    }

    /** Starts a correction draft from a published edition; lesson IDs are new, assets are reused. */
    @Transactional
    DraftView startDraft(UUID courseId, UUID fromVersionId, Actor actor) {
        access.lockCourse(courseId, actor);
        if (courses.findDraft(courseId).isPresent()) {
            throw ApiException.conflict(CourseErrors.DRAFT_EXISTS, "This course already has a draft");
        }
        VersionRow source = courses.findVersion(fromVersionId)
                .filter(version -> !version.isDraft() && version.courseId().equals(courseId))
                .orElseThrow(ApiException::notFound);
        UUID draftId = UUID.randomUUID();
        courses.insertDraft(draftId, courseId, source.title(), source.description(), source.id(), clock.instant());
        content.replaceTree(draftId, withNewIds(content.readTree(source.id())));
        content.replaceRequiredExams(draftId, content.requiredExams(source.id()));
        audit.record(AuditEntry.success(actor.userId(), "course.draft-started", "course", courseId, Map.of("fromEdition", source.edition())));
        return draftView(requireDraft(courseId));
    }

    @Transactional
    void setStatus(UUID courseId, CourseStatus status, Actor actor) {
        CourseRow course = access.lockCourse(courseId, actor);
        courses.updateCourse(courseId, course.title(), status, clock.instant());
        audit.record(AuditEntry.success(actor.userId(), "course.status-changed", "course", courseId, Map.of("status", status)));
    }

    @Transactional
    void setTeachers(UUID courseId, Set<UUID> teacherIds, Actor actor) {
        if (!actor.isAdministrator()) {
            throw ApiException.accessDenied("Administrator role required");
        }
        access.lockCourse(courseId, actor);
        Map<UUID, AccountSummary> accounts = identity.summaries(teacherIds);
        boolean valid = !teacherIds.isEmpty() && accounts.size() == teacherIds.size() && accounts.values().stream()
                .allMatch(account -> account.status() == AccountStatus.ACTIVE && account.roles().contains(Role.TEACHER));
        if (!valid) {
            throw ApiException.invalid("teacherIds", "Choose at least one active teacher");
        }
        courses.replaceTeachers(courseId, teacherIds);
        audit.record(AuditEntry.success(actor.userId(), "course.teachers-changed", "course", courseId, Map.of("teachers", teacherIds.size())));
    }

    /** Shows the draft (default when one exists) or any edition exactly as students would see it. */
    @Transactional(readOnly = true)
    PreviewView preview(UUID courseId, UUID versionId, Actor actor) {
        access.requireCourse(courseId, actor);
        VersionRow version = (versionId == null
                ? courses.findDraft(courseId).or(() -> courses.editions(courseId).stream().findFirst())
                : courses.findVersion(versionId).filter(row -> row.courseId().equals(courseId)))
                .orElseThrow(ApiException::notFound);
        return new PreviewView(version.id(), courseId, version.edition(), version.title(), version.description(), content.readTree(version.id()),
                content.requiredExams(version.id()));
    }

    private VersionRow requireDraft(UUID courseId) {
        return courses.findDraft(courseId)
                .orElseThrow(() -> ApiException.conflict(CourseErrors.NO_DRAFT, "This course has no draft; start one from an edition"));
    }

    private DraftView draftView(VersionRow draft) {
        return new DraftView(draft.id(), draft.courseId(), draft.version(), draft.title(), draft.description(), content.readTree(draft.id()),
                content.requiredExams(draft.id()));
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

    private static Integer editionOf(UUID versionId, List<VersionRow> editions) {
        return editions.stream().filter(row -> row.id().equals(versionId)).map(VersionRow::edition).findFirst().orElse(null);
    }
}
