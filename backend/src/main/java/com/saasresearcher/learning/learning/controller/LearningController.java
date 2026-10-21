package com.saasresearcher.learning.learning.controller;

import com.saasresearcher.learning.classrooms.api.ClassStatus;
import com.saasresearcher.learning.classrooms.api.EnrollmentAccess;
import com.saasresearcher.learning.classrooms.api.EnrollmentStatus;
import com.saasresearcher.learning.classrooms.api.EnrollmentView;
import com.saasresearcher.learning.classrooms.api.StudentEnrollment;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.courses.api.CourseCatalog;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.learning.api.LessonSummary;
import com.saasresearcher.learning.learning.api.ProgressCommand;
import com.saasresearcher.learning.learning.dto.ProgressView;
import com.saasresearcher.learning.learning.service.ProgressService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** A student's own dashboard, course player and progress writes. */
@RestController
public class LearningController {

    private final EnrollmentAccess enrollments;
    private final CourseCatalog catalog;
    private final ProgressService progress;

    LearningController(EnrollmentAccess enrollments, CourseCatalog catalog, ProgressService progress) {
        this.enrollments = enrollments;
        this.catalog = catalog;
        this.progress = progress;
    }

    /** Includes historical enrollments (withdrawn, archived) so students keep their record. */
    @GetMapping("/me/learning")
    @Transactional(readOnly = true)
    List<DashboardEntry> dashboard(Actor actor) {
        return enrollments.enrollmentsOf(actor.userId()).stream().map(entry -> {
            PublishedCourse course = catalog.requirePublished(entry.enrollment().courseVersionId());
            return new DashboardEntry(entry.enrollment().id(), entry.className(), course.title(), course.edition(), entry.enrollment().status(),
                    entry.classStatus(), entry.accessOpen(), entry.startsAt(), entry.endsAt(),
                    progress.summary(course, progress.forEnrollment(entry.enrollment().id())));
        }).toList();
    }

    /** The course player; only while the enrollment is active and the class open. */
    @GetMapping("/me/enrollments/{id}")
    @Transactional(readOnly = true)
    Player player(@PathVariable UUID id, Actor actor) {
        EnrollmentView enrollment = enrollments.requireActive(id, actor);
        StudentEnrollment own = enrollments.requireOwn(id, actor);
        PublishedCourse course = catalog.requirePublished(enrollment.courseVersionId());
        List<ProgressView> lessons = progress.forEnrollment(id);
        return new Player(id, own.className(), course, lessons, progress.summary(course, lessons));
    }

    @PutMapping("/enrollments/{id}/lessons/{lessonId}/progress")
    ProgressView save(@PathVariable UUID id, @PathVariable UUID lessonId, @RequestBody ProgressCommand command, Actor actor) {
        return progress.save(id, lessonId, command, actor);
    }

    record DashboardEntry(UUID enrollmentId, String className, String courseTitle, int edition, EnrollmentStatus enrollmentStatus,
            ClassStatus classStatus, boolean accessOpen, Instant startsAt, Instant endsAt, LessonSummary summary) {
    }

    record Player(UUID enrollmentId, String className, PublishedCourse course, List<ProgressView> progress, LessonSummary summary) {
    }
}
