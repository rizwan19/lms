package com.saasresearcher.learning.assessments.internal;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.ExamCatalog;
import com.saasresearcher.learning.assessments.api.PublishedExam;
import com.saasresearcher.learning.assessments.internal.AttemptRows.AttemptRow;
import com.saasresearcher.learning.classrooms.api.EnrollmentAccess;
import com.saasresearcher.learning.classrooms.api.StudentEnrollment;
import com.saasresearcher.learning.common.Actor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** A student's exams for one of their enrollments, with their own attempts. No keys, no unpublished scores. */
@RestController
class StudentExamController {

    private final EnrollmentAccess enrollments;
    private final AssignmentRepository assignments;
    private final AttemptRepository attempts;
    private final ExamCatalog catalog;
    private final Clock clock;

    StudentExamController(EnrollmentAccess enrollments, AssignmentRepository assignments, AttemptRepository attempts, ExamCatalog catalog,
            Clock clock) {
        this.enrollments = enrollments;
        this.assignments = assignments;
        this.attempts = attempts;
        this.catalog = catalog;
        this.clock = clock;
    }

    @GetMapping("/me/enrollments/{id}/exams")
    @Transactional(readOnly = true)
    List<StudentExam> exams(@PathVariable UUID id, Actor actor) {
        StudentEnrollment own = enrollments.requireOwn(id, actor);
        Instant now = clock.instant();
        return assignments.forClass(own.enrollment().classId()).stream().map(assignment -> {
            PublishedExam exam = catalog.requirePublished(assignment.examVersionId());
            List<AttemptRow> mine = attempts.forEnrollment(id, assignment.id());
            UUID active = mine.stream().filter(attempt -> attempt.status() == AttemptStatus.IN_PROGRESS && !attempt.isExpired(now))
                    .map(AttemptRow::id).findFirst().orElse(null);
            boolean canStart = own.accessOpen() && assignment.isOpen(now) && (active != null || mine.size() < assignment.schedule().maxAttempts());
            return new StudentExam(assignment.id(), exam.title(), assignment.required(), exam.questionCount(), exam.maxPoints(),
                    assignment.schedule().opensAt(), assignment.schedule().closesAt(), assignment.schedule().durationMinutes(),
                    assignment.schedule().maxAttempts(), mine.size(), active, canStart,
                    mine.stream().map(attempt -> new AttemptSummary(attempt.id(), attempt.attemptNumber(), attempt.status(), attempt.finalizedAt())).toList());
        }).toList();
    }

    record AttemptSummary(UUID id, int attemptNumber, AttemptStatus status, Instant finalizedAt) {
    }

    record StudentExam(UUID assignmentId, String title, boolean required, int questionCount, BigDecimal maxPoints, Instant opensAt,
            Instant closesAt, Integer durationMinutes, int maxAttempts, int attemptsUsed, UUID activeAttemptId, boolean canStart,
            List<AttemptSummary> attempts) {
    }
}
