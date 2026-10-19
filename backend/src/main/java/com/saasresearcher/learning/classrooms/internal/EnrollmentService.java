package com.saasresearcher.learning.classrooms.internal;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.classrooms.api.ClassStatus;
import com.saasresearcher.learning.classrooms.api.EnrollmentAccess;
import com.saasresearcher.learning.classrooms.api.EnrollmentStatus;
import com.saasresearcher.learning.classrooms.api.EnrollmentView;
import com.saasresearcher.learning.classrooms.api.StudentEnrollment;
import com.saasresearcher.learning.classrooms.internal.ClassViews.RosterRow;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.notifications.api.NotificationRequest;
import com.saasresearcher.learning.notifications.api.Notifications;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rosters (ACTIVE, SUSPENDED, WITHDRAWN) and the enrollment checks other modules rely on. */
@Service
class EnrollmentService implements EnrollmentAccess {

    private final EnrollmentRepository enrollments;
    private final ClassroomRepository classes;
    private final ClassAuthorization authorization;
    private final IdentityAccess identity;
    private final Notifications notifications;
    private final AuditLog audit;
    private final Clock clock;

    EnrollmentService(EnrollmentRepository enrollments, ClassroomRepository classes, ClassAuthorization authorization,
            IdentityAccess identity, Notifications notifications, AuditLog audit, Clock clock) {
        this.enrollments = enrollments;
        this.classes = classes;
        this.authorization = authorization;
        this.identity = identity;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    RosterRow enroll(UUID classId, String username, Actor actor) {
        Classroom classroom = authorization.lockClass(classId, actor);
        if (classroom.status() == ClassStatus.ARCHIVED) {
            throw ApiException.conflict(ClassErrors.INVALID_CLASS_STATE, "Archived classes cannot take new students");
        }
        AccountSummary student = identity.findByUsername(username == null ? "" : username)
                .filter(account -> account.status() == AccountStatus.ACTIVE && account.roles().contains(Role.STUDENT))
                .orElseThrow(() -> ApiException.invalid("username", "No active student has that username"));
        Enrollment enrollment = enroll(classroom, student.id(), actor);
        return toRow(enrollment, student);
    }

    /** Enrolls an already validated student in a locked, non-archived class; used by single and bulk enrollment. */
    Enrollment enroll(Classroom classroom, UUID studentId, Actor actor) {
        if (enrollments.findByClassIdAndStudentId(classroom.id(), studentId).isPresent()) {
            throw ApiException.conflict(ClassErrors.ALREADY_ENROLLED, "The student is already on this roster");
        }
        Enrollment enrollment = enrollments.save(new Enrollment(classroom.id(), studentId, clock.instant()));
        notifications.notify(new NotificationRequest(studentId, "enrollment.created", "Enrolled in " + classroom.name(),
                "You can start learning when the class opens.", "/student/enrollments/" + enrollment.id(), "enrolled:" + enrollment.id()));
        audit.record(AuditEntry.success(actor.userId(), "enrollment.created", "enrollment", enrollment.id(), Map.of("classId", classroom.id())));
        return enrollment;
    }

    @Transactional(readOnly = true)
    PageResponse<RosterRow> roster(UUID classId, EnrollmentStatus status, PageQuery query, Actor actor) {
        authorization.requireClass(classId, actor);
        Page<Enrollment> page = status == null
                ? enrollments.findByClassId(classId, query.pageable(Sort.by("enrolledAt", "id")))
                : enrollments.findByClassIdAndStatus(classId, status, query.pageable(Sort.by("enrolledAt", "id")));
        Map<UUID, AccountSummary> students = identity.summaries(page.map(Enrollment::studentId).toList());
        return PageResponse.of(page, enrollment -> toRow(enrollment, students.get(enrollment.studentId())));
    }

    @Transactional
    RosterRow changeStatus(UUID classId, UUID enrollmentId, EnrollmentStatus status, Actor actor) {
        authorization.requireClass(classId, actor);
        Enrollment enrollment = enrollments.lockById(enrollmentId)
                .filter(candidate -> candidate.classId().equals(classId))
                .orElseThrow(ApiException::notFound);
        enrollment.changeStatus(status, clock.instant());
        audit.record(AuditEntry.success(actor.userId(), "enrollment.status-changed", "enrollment", enrollmentId, Map.of("status", status)));
        return toRow(enrollment, identity.summaries(List.of(enrollment.studentId())).get(enrollment.studentId()));
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentView requireActive(UUID enrollmentId, Actor actor) {
        return requireUsable(enrollments.findById(enrollmentId), actor);
    }

    @Override
    @Transactional
    public EnrollmentView lockActive(UUID enrollmentId, Actor actor) {
        return requireUsable(enrollments.lockById(enrollmentId), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentEnrollment requireOwn(UUID enrollmentId, Actor actor) {
        Enrollment enrollment = enrollments.findById(enrollmentId)
                .filter(candidate -> candidate.studentId().equals(actor.userId()))
                .orElseThrow(ApiException::notFound);
        return toStudentEnrollment(enrollment, classes.findById(enrollment.classId()).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentEnrollment> enrollmentsOf(UUID studentId) {
        List<Enrollment> own = enrollments.findByStudentIdOrderByEnrolledAtDesc(studentId);
        Map<UUID, Classroom> byId = classes.findByIdIn(own.stream().map(Enrollment::classId).toList()).stream()
                .collect(Collectors.toMap(Classroom::id, Function.identity()));
        return own.stream().map(enrollment -> toStudentEnrollment(enrollment, byId.get(enrollment.classId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentView require(UUID enrollmentId) {
        Enrollment enrollment = enrollments.findById(enrollmentId).orElseThrow(ApiException::notFound);
        return enrollment.toView(classes.findById(enrollment.classId()).orElseThrow().courseVersionId());
    }

    @Override
    @Transactional
    public EnrollmentView lock(UUID enrollmentId) {
        Enrollment enrollment = enrollments.lockById(enrollmentId).orElseThrow(ApiException::notFound);
        return enrollment.toView(classes.findById(enrollment.classId()).orElseThrow().courseVersionId());
    }

    private EnrollmentView requireUsable(Optional<Enrollment> found, Actor actor) {
        Enrollment enrollment = found.filter(candidate -> candidate.studentId().equals(actor.userId())).orElseThrow(ApiException::notFound);
        Classroom classroom = classes.findById(enrollment.classId()).orElseThrow();
        if (enrollment.status() != EnrollmentStatus.ACTIVE || !classroom.isOpen(clock.instant())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ClassErrors.ENROLLMENT_INACTIVE,
                    "This class is not open for learning right now");
        }
        return enrollment.toView(classroom.courseVersionId());
    }

    private StudentEnrollment toStudentEnrollment(Enrollment enrollment, Classroom classroom) {
        boolean open = enrollment.status() == EnrollmentStatus.ACTIVE && classroom.isOpen(clock.instant());
        return new StudentEnrollment(enrollment.toView(classroom.courseVersionId()), classroom.name(), classroom.status(),
                classroom.startsAt(), classroom.endsAt(), enrollment.enrolledAt(), open);
    }

    private static RosterRow toRow(Enrollment enrollment, AccountSummary student) {
        return new RosterRow(enrollment.id(), enrollment.studentId(), student.username(), student.displayName(), enrollment.status(),
                enrollment.enrolledAt());
    }
}
