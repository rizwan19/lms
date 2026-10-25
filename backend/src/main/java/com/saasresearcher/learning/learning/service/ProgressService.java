package com.saasresearcher.learning.learning.service;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.courses.api.CourseCatalogService;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.enrollment.api.EnrollmentAccessService;
import com.saasresearcher.learning.enrollment.api.EnrollmentView;
import com.saasresearcher.learning.learning.api.LearningProgress;
import com.saasresearcher.learning.learning.api.LessonSummary;
import com.saasresearcher.learning.learning.api.ProgressCommand;
import com.saasresearcher.learning.learning.api.ProgressObserver;
import com.saasresearcher.learning.learning.dto.ProgressView;
import com.saasresearcher.learning.learning.entity.LessonProgress;
import com.saasresearcher.learning.learning.repository.ProgressRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lesson progress: completion is an explicit action, the saved resume position only moves forward, and every write
 * carries the expected revision under the enrollment lock. Lessons always come from the enrollment's pinned edition.
 */
@Service
public class ProgressService implements LearningProgress {

    static final String PROGRESS_REVISION_CONFLICT = "PROGRESS_REVISION_CONFLICT";
    static final String COMPLETION_LOCKED = "COMPLETION_LOCKED";
    private static final long MAX_RESUME_SECONDS = 24 * 60 * 60;

    private final ProgressRepository progress;
    private final EnrollmentAccessService enrollments;
    private final CourseCatalogService catalog;
    private final ObjectProvider<ProgressObserver> observers;
    private final Clock clock;

    ProgressService(ProgressRepository progress, EnrollmentAccessService enrollments, CourseCatalogService catalog, ObjectProvider<ProgressObserver> observers,
                    Clock clock) {
        this.progress = progress;
        this.enrollments = enrollments;
        this.catalog = catalog;
        this.observers = observers;
        this.clock = clock;
    }

    @Transactional
    public ProgressView save(UUID enrollmentId, UUID lessonId, ProgressCommand command, Actor actor) {
        EnrollmentView enrollment = enrollments.lockActive(enrollmentId, actor);
        catalog.requirePublished(enrollment.courseVersionId()).lesson(lessonId).orElseThrow(ApiException::notFound);
        LessonProgress record = progress.findByEnrollmentIdAndLessonId(enrollmentId, lessonId)
                .orElseGet(() -> new LessonProgress(enrollmentId, enrollment.courseId(), enrollment.courseVersionId(), lessonId));
        if (command.expectedRevision() != record.getRevision()) {
            throw ApiException.conflict(PROGRESS_REVISION_CONFLICT, "Progress was saved from another tab; reloading");
        }
        boolean wasCompleted = record.completed();
        if (!command.completed() && wasCompleted && enrollments.isLearningLocked(enrollmentId)) {
            throw ApiException.conflict(COMPLETION_LOCKED, "Lessons cannot be marked incomplete after the course is completed");
        }
        Instant now = clock.instant();
        record.record(command.completed(), Math.clamp(command.resumeSeconds(), 0, MAX_RESUME_SECONDS), now);
        progress.saveAndFlush(record);
        if (record.completed() != wasCompleted) {
            observers.forEach(observer -> observer.progressChanged(enrollmentId));
        }
        return ProgressView.of(record);
    }

    @Override
    @Transactional(readOnly = true)
    public LessonSummary summary(UUID enrollmentId) {
        EnrollmentView enrollment = enrollments.require(enrollmentId);
        return summary(catalog.requirePublished(enrollment.courseVersionId()), forEnrollment(enrollmentId));
    }

    @Transactional(readOnly = true)
    public List<ProgressView> forEnrollment(UUID enrollmentId) {
        return progress.findByEnrollmentId(enrollmentId).stream().map(ProgressView::of).toList();
    }

    public LessonSummary summary(PublishedCourse course, List<ProgressView> records) {
        Set<UUID> required = course.requiredLessonIds();
        int completed = (int) records.stream().filter(ProgressView::completed).filter(record -> required.contains(record.lessonId())).count();
        Instant lastActivity = records.stream().map(ProgressView::lastActivityAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return new LessonSummary(required.size(), completed, lastActivity);
    }
}
