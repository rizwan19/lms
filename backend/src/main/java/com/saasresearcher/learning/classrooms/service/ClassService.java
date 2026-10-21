package com.saasresearcher.learning.classrooms.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.classrooms.api.ClassLifecycleListener;
import com.saasresearcher.learning.classrooms.api.ClassStatus;
import com.saasresearcher.learning.classrooms.dto.ClassViews.ClassDetail;
import com.saasresearcher.learning.classrooms.dto.ClassViews.ClassSummary;
import com.saasresearcher.learning.classrooms.dto.ClassViews.TeacherView;
import com.saasresearcher.learning.classrooms.entity.Classroom;
import com.saasresearcher.learning.classrooms.repository.ClassroomRepository;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.courses.api.CourseCatalog;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Class lifecycle DRAFT → ACTIVE → ARCHIVED, dates and teacher assignment. */
@Service
public class ClassService {

    private final ClassroomRepository classes;
    private final ClassAuthorization authorization;
    private final CourseCatalog catalog;
    private final IdentityAccess identity;
    private final ObjectProvider<ClassLifecycleListener> listeners;
    private final AuditLog audit;
    private final Clock clock;

    ClassService(ClassroomRepository classes, ClassAuthorization authorization, CourseCatalog catalog, IdentityAccess identity,
            ObjectProvider<ClassLifecycleListener> listeners, AuditLog audit, Clock clock) {
        this.classes = classes;
        this.authorization = authorization;
        this.catalog = catalog;
        this.identity = identity;
        this.listeners = listeners;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ClassDetail create(UUID courseVersionId, String name, Instant startsAt, Instant endsAt, Actor actor) {
        authorization.requireStaff(actor);
        PublishedCourse course = catalog.requirePublished(courseVersionId);
        if (!actor.isAdministrator() && !catalog.isCourseTeacher(course.courseId(), actor.userId())) {
            throw ApiException.notFound();
        }
        if (course.archived()) {
            throw ApiException.invalid("courseVersionId", "Archived courses cannot start new classes");
        }
        requireDates(startsAt, endsAt);
        Classroom classroom = new Classroom(course.courseId(), course.versionId(), requireName(name), startsAt, endsAt, actor.userId(), clock.instant());
        classes.save(classroom);
        audit.record(AuditEntry.success(actor.userId(), "class.created", "class", classroom.id(), Map.of("edition", course.edition())));
        return detail(classroom, course);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassSummary> list(PageQuery query, Actor actor) {
        authorization.requireStaff(actor);
        Page<Classroom> page = actor.isAdministrator()
                ? classes.findAll(query.pageable(Sort.by("name", "id")))
                : classes.findByTeacher(actor.userId(), query.pageable(Sort.by("name", "id")));
        Map<UUID, PublishedCourse> courses = new HashMap<>();
        return PageResponse.of(page, classroom -> {
            PublishedCourse course = courses.computeIfAbsent(classroom.courseVersionId(), catalog::requirePublished);
            return new ClassSummary(classroom.id(), classroom.name(), classroom.status(), course.title(), course.edition(),
                    classroom.startsAt(), classroom.endsAt());
        });
    }

    @Transactional(readOnly = true)
    public ClassDetail get(UUID classId, Actor actor) {
        Classroom classroom = authorization.requireClass(classId, actor);
        return detail(classroom, catalog.requirePublished(classroom.courseVersionId()));
    }

    @Transactional
    public ClassDetail update(UUID classId, String name, Instant startsAt, Instant endsAt, long expectedRevision, Actor actor) {
        Classroom classroom = authorization.requireClass(classId, actor);
        Revisions.require(expectedRevision, classroom.version());
        requireNotArchived(classroom);
        requireDates(startsAt, endsAt);
        classroom.reschedule(requireName(name), startsAt, endsAt, clock.instant());
        classes.flush();
        audit.record(AuditEntry.success(actor.userId(), "class.updated", "class", classId,
                Map.of("startsAt", String.valueOf(startsAt), "endsAt", String.valueOf(endsAt))));
        return get(classId, actor);
    }

    @Transactional
    public ClassDetail activate(UUID classId, Actor actor) {
        Classroom classroom = authorization.lockClass(classId, actor);
        if (classroom.status() != ClassStatus.DRAFT) {
            throw ApiException.conflict(ClassErrors.INVALID_CLASS_STATE, "Only draft classes can be activated");
        }
        classroom.activate(clock.instant());
        classes.flush();
        listeners.forEach(listener -> listener.classActivated(classroom.toInfo()));
        audit.record(AuditEntry.success(actor.userId(), "class.activated", "class", classId));
        return get(classId, actor);
    }

    @Transactional
    public ClassDetail archive(UUID classId, Actor actor) {
        Classroom classroom = authorization.lockClass(classId, actor);
        requireNotArchived(classroom);
        classroom.archive(clock.instant());
        audit.record(AuditEntry.success(actor.userId(), "class.archived", "class", classId));
        return get(classId, actor);
    }

    @Transactional
    public ClassDetail setTeachers(UUID classId, Set<UUID> teacherIds, Actor actor) {
        Classroom classroom = authorization.lockClass(classId, actor);
        Map<UUID, AccountSummary> accounts = identity.summaries(teacherIds);
        boolean valid = !teacherIds.isEmpty() && accounts.size() == teacherIds.size() && accounts.values().stream()
                .allMatch(account -> account.status() == AccountStatus.ACTIVE && account.roles().contains(Role.TEACHER));
        if (!valid) {
            throw ApiException.invalid("teacherIds", "Choose at least one active teacher");
        }
        classroom.replaceTeachers(teacherIds, clock.instant());
        audit.record(AuditEntry.success(actor.userId(), "class.teachers-changed", "class", classId, Map.of("teachers", teacherIds.size())));
        return detail(classroom, catalog.requirePublished(classroom.courseVersionId()));
    }

    private ClassDetail detail(Classroom classroom, PublishedCourse course) {
        var teachers = identity.summaries(classroom.teacherIds()).values().stream()
                .map(account -> new TeacherView(account.id(), account.displayName()))
                .sorted(Comparator.comparing(TeacherView::displayName))
                .toList();
        return new ClassDetail(classroom.id(), classroom.name(), classroom.status(), course.courseId(), course.versionId(), course.title(),
                course.edition(), classroom.startsAt(), classroom.endsAt(), teachers, classroom.version());
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 200) {
            throw ApiException.invalid("name", "Name must be 1-200 characters");
        }
        return trimmed;
    }

    private static void requireDates(Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw ApiException.invalid("endsAt", "The end must be after the start");
        }
    }

    private static void requireNotArchived(Classroom classroom) {
        if (classroom.status() == ClassStatus.ARCHIVED) {
            throw ApiException.conflict(ClassErrors.INVALID_CLASS_STATE, "Archived classes cannot be changed");
        }
    }
}
