package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.ExamCatalog;
import com.saasresearcher.learning.assessments.api.PublishedExam;
import com.saasresearcher.learning.assessments.dto.StudentExamViews.AttemptSummary;
import com.saasresearcher.learning.assessments.dto.StudentExamViews.StudentExam;
import com.saasresearcher.learning.assessments.entity.AttemptRows.AttemptRow;
import com.saasresearcher.learning.assessments.repository.AssignmentRepository;
import com.saasresearcher.learning.assessments.repository.AttemptRepository;
import com.saasresearcher.learning.classrooms.api.EnrollmentAccess;
import com.saasresearcher.learning.classrooms.api.StudentEnrollment;
import com.saasresearcher.learning.common.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A student's exams for one of their enrollments, with their own attempts. */
@Service
public class StudentExamService {

    private final EnrollmentAccess enrollments;
    private final AssignmentRepository assignments;
    private final AttemptRepository attempts;
    private final ExamCatalog catalog;
    private final Clock clock;

    StudentExamService(EnrollmentAccess enrollments, AssignmentRepository assignments, AttemptRepository attempts, ExamCatalog catalog,
            Clock clock) {
        this.enrollments = enrollments;
        this.assignments = assignments;
        this.attempts = attempts;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StudentExam> exams(UUID enrollmentId, Actor actor) {
        StudentEnrollment own = enrollments.requireOwn(enrollmentId, actor);
        Instant now = clock.instant();
        return assignments.forClass(own.enrollment().classId()).stream().map(assignment -> {
            PublishedExam exam = catalog.requirePublished(assignment.examVersionId());
            List<AttemptRow> mine = attempts.forEnrollment(enrollmentId, assignment.id());
            UUID active = mine.stream().filter(attempt -> attempt.status() == AttemptStatus.IN_PROGRESS && !attempt.isExpired(now))
                    .map(AttemptRow::id).findFirst().orElse(null);
            boolean canStart = own.accessOpen() && assignment.isOpen(now) && (active != null || mine.size() < assignment.schedule().maxAttempts());
            return new StudentExam(assignment.id(), exam.title(), assignment.required(), exam.questionCount(), exam.maxPoints(),
                    assignment.schedule().opensAt(), assignment.schedule().closesAt(), assignment.schedule().durationMinutes(),
                    assignment.schedule().maxAttempts(), mine.size(), active, canStart,
                    mine.stream().map(attempt -> new AttemptSummary(attempt.id(), attempt.attemptNumber(), attempt.status(), attempt.finalizedAt())).toList());
        }).toList();
    }
}
