package com.saasresearcher.learning.learning.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.courses.api.CourseCatalogService;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.enrollment.api.EnrollmentAccessService;
import com.saasresearcher.learning.enrollment.api.EnrollmentStatus;
import com.saasresearcher.learning.enrollment.api.EnrollmentView;
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

/** A learner's own dashboard, course player and progress writes. Teachers learn through the same routes. */
@RestController
public class LearningController {

    private final EnrollmentAccessService enrollments;
    private final CourseCatalogService catalog;
    private final ProgressService progress;

    LearningController(EnrollmentAccessService enrollments, CourseCatalogService catalog, ProgressService progress) {
        this.enrollments = enrollments;
        this.catalog = catalog;
        this.progress = progress;
    }

    /** Includes historical enrollments (withdrawn, suspended) so learners keep their record. */
    @GetMapping("/me/learning")
    @Transactional(readOnly = true)
    List<DashboardEntry> dashboard(Actor actor) {
        return enrollments.enrollmentsOf(actor.userId()).stream().map(entry -> {
            PublishedCourse course = catalog.requirePublished(entry.enrollment().courseVersionId());
            return new DashboardEntry(entry.enrollment().id(), course.courseId(), course.title(), course.edition(), entry.enrollment().status(),
                    entry.accessOpen(), entry.grantedAt(), progress.summary(course, progress.forEnrollment(entry.enrollment().id())));
        }).toList();
    }

    /** The course player on the enrollment's pinned edition; only while the enrollment is active. */
    @GetMapping("/me/enrollments/{id}/content")
    @Transactional(readOnly = true)
    Player player(@PathVariable UUID id, Actor actor) {
        EnrollmentView enrollment = enrollments.requireActive(id, actor);
        PublishedCourse course = catalog.requirePublished(enrollment.courseVersionId());
        List<ProgressView> lessons = progress.forEnrollment(id);
        return new Player(id, course, lessons, progress.summary(course, lessons));
    }

    @PutMapping("/enrollments/{id}/lessons/{lessonId}/progress")
    ProgressView save(@PathVariable UUID id, @PathVariable UUID lessonId, @RequestBody ProgressCommand command, Actor actor) {
        return progress.save(id, lessonId, command, actor);
    }

    record DashboardEntry(UUID enrollmentId, UUID courseId, String courseTitle, int edition, EnrollmentStatus enrollmentStatus, boolean accessOpen,
            Instant grantedAt, LessonSummary summary) {
    }

    record Player(UUID enrollmentId, PublishedCourse course, List<ProgressView> progress, LessonSummary summary) {
    }
}
