package com.saasresearcher.learning.assessments.internal;

import com.saasresearcher.learning.assessments.api.ExamCatalog;
import com.saasresearcher.learning.assessments.api.PublishedExam;
import com.saasresearcher.learning.assessments.internal.ExamRows.ExamSummary;
import com.saasresearcher.learning.assessments.internal.Schedule.FeedbackPolicy;
import com.saasresearcher.learning.assessments.internal.Schedule.ResultPolicy;
import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.classrooms.api.ClassDirectory;
import com.saasresearcher.learning.classrooms.api.ClassInfo;
import com.saasresearcher.learning.classrooms.api.ClassLifecycleListener;
import com.saasresearcher.learning.classrooms.api.TeachingAccess;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.ErrorCodes;
import com.saasresearcher.learning.courses.api.CourseCatalog;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class exam schedules. Required assignments come only from the course edition when the class is activated and
 * keep their exam edition forever; teachers may add optional practice assignments and adjust schedules.
 */
@Service
class ExamAssignmentService implements ClassLifecycleListener {

    private final AssignmentRepository assignments;
    private final ExamRepository exams;
    private final ExamCatalog catalog;
    private final CourseCatalog courses;
    private final ClassDirectory classes;
    private final TeachingAccess teaching;
    private final AuditLog audit;
    private final Clock clock;

    ExamAssignmentService(AssignmentRepository assignments, ExamRepository exams, ExamCatalog catalog, CourseCatalog courses,
            ClassDirectory classes, TeachingAccess teaching, AuditLog audit, Clock clock) {
        this.assignments = assignments;
        this.exams = exams;
        this.catalog = catalog;
        this.courses = courses;
        this.classes = classes;
        this.teaching = teaching;
        this.audit = audit;
        this.clock = clock;
    }

    record ScheduleCommand(Instant opensAt, Instant closesAt, Integer durationMinutes, Integer maxAttempts, ResultPolicy resultPolicy,
            FeedbackPolicy feedbackPolicy) {
    }

    record AssignmentView(UUID id, UUID classId, UUID examVersionId, String examTitle, int edition, int questionCount, BigDecimal maxPoints,
            boolean mixed, boolean required, Schedule schedule, long revision) {
    }

    /** Freezes the edition's required exams into class assignments, opening and closing with the class by default. */
    @Override
    public void classActivated(ClassInfo classInfo) {
        for (UUID examVersionId : courses.requirePublished(classInfo.courseVersionId()).requiredExamVersionIds()) {
            PublishedExam exam = catalog.requirePublished(examVersionId);
            assignments.insert(UUID.randomUUID(), classInfo.id(), examVersionId, true,
                    Schedule.defaults(classInfo.startsAt(), classInfo.endsAt(), exam.mixed()), clock.instant());
        }
    }

    @Transactional(readOnly = true)
    List<AssignmentView> forClass(UUID classId, Actor actor) {
        teaching.requireClassTeacher(classId, actor);
        return assignments.forClass(classId).stream().map(this::view).toList();
    }

    /** Published exams of the class's course that can be added as optional practice. */
    @Transactional(readOnly = true)
    List<ExamSummary> available(UUID classId, Actor actor) {
        teaching.requireClassTeacher(classId, actor);
        return exams.examsOfCourse(classes.require(classId).courseId()).stream().filter(exam -> exam.latestVersionId() != null).toList();
    }

    @Transactional
    AssignmentView createPractice(UUID classId, UUID examVersionId, ScheduleCommand command, Actor actor) {
        teaching.requireClassTeacher(classId, actor);
        ClassInfo classInfo = classes.require(classId);
        PublishedExam exam = catalog.requirePublished(examVersionId);
        if (!exam.courseId().equals(classInfo.courseId())) {
            throw ApiException.invalid("examVersionId", "Choose an exam from this class's course");
        }
        UUID id = UUID.randomUUID();
        if (!assignments.insert(id, classId, examVersionId, false, schedule(command, exam, false), clock.instant())) {
            throw ApiException.conflict("ALREADY_ASSIGNED", "This exam edition is already scheduled for the class");
        }
        audit.record(AuditEntry.success(actor.userId(), "exam-assignment.created", "exam-assignment", id, Map.of("classId", classId)));
        return view(assignments.find(id).orElseThrow());
    }

    /** Only schedule and policies change; the exam edition and the required flag are fixed. */
    @Transactional
    AssignmentView update(UUID assignmentId, long expectedRevision, ScheduleCommand command, Actor actor) {
        AssignmentRow current = assignments.find(assignmentId).orElseThrow(ApiException::notFound);
        teaching.requireClassTeacher(current.classId(), actor);
        Schedule schedule = schedule(command, catalog.requirePublished(current.examVersionId()), current.required());
        if (!assignments.update(assignmentId, expectedRevision, schedule, clock.instant())) {
            throw ApiException.conflict(ErrorCodes.REVISION_CONFLICT, "The schedule was changed by another request; reload and retry");
        }
        audit.record(AuditEntry.success(actor.userId(), "exam-assignment.rescheduled", "exam-assignment", assignmentId,
                Map.of("opensAt", String.valueOf(schedule.opensAt()), "closesAt", String.valueOf(schedule.closesAt()))));
        return view(assignments.find(assignmentId).orElseThrow());
    }

    private static Schedule schedule(ScheduleCommand command, PublishedExam exam, boolean required) {
        return Schedule.validated(command.opensAt(), command.closesAt(), command.durationMinutes(), command.maxAttempts(), command.resultPolicy(),
                command.feedbackPolicy(), exam.mixed(), required);
    }

    private AssignmentView view(AssignmentRow row) {
        PublishedExam exam = catalog.requirePublished(row.examVersionId());
        return new AssignmentView(row.id(), row.classId(), row.examVersionId(), exam.title(), exam.edition(), exam.questionCount(), exam.maxPoints(),
                exam.mixed(), row.required(), row.schedule(), row.version());
    }
}
