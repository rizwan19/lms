package com.saasresearcher.learning.learning.internal;

import com.saasresearcher.learning.classrooms.api.EnrollmentAccess;
import com.saasresearcher.learning.classrooms.api.EnrollmentView;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.courses.api.CourseCatalog;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.learning.api.LearningProgress;
import com.saasresearcher.learning.learning.api.LessonSummary;
import com.saasresearcher.learning.learning.api.ProgressCommand;
import com.saasresearcher.learning.learning.api.ProgressObserver;
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
 * Lesson progress (spec §10): completion is an explicit action, the saved resume position only moves forward,
 * and every write carries the expected revision under the enrollment lock.
 */
@Service
class ProgressService implements LearningProgress {

    static final String PROGRESS_REVISION_CONFLICT = "PROGRESS_REVISION_CONFLICT";
    private static final long MAX_RESUME_SECONDS = 24 * 60 * 60;

    private final ProgressRepository progress;
    private final EnrollmentAccess enrollments;
    private final CourseCatalog catalog;
    private final ObjectProvider<ProgressObserver> observers;
    private final Clock clock;

    ProgressService(ProgressRepository progress, EnrollmentAccess enrollments, CourseCatalog catalog,
            ObjectProvider<ProgressObserver> observers, Clock clock) {
        this.progress = progress;
        this.enrollments = enrollments;
        this.catalog = catalog;
        this.observers = observers;
        this.clock = clock;
    }

    @Transactional
    ProgressView save(UUID enrollmentId, UUID lessonId, ProgressCommand command, Actor actor) {
        EnrollmentView enrollment = enrollments.lockActive(enrollmentId, actor);
        catalog.requirePublished(enrollment.courseVersionId()).lesson(lessonId).orElseThrow(ApiException::notFound);
        ProgressView current = progress.find(enrollmentId, lessonId).orElse(null);
        long revision = current == null ? 0 : current.revision();
        if (command.expectedRevision() != revision) {
            throw ApiException.conflict(PROGRESS_REVISION_CONFLICT, "Progress was saved from another tab; reloading");
        }
        Instant now = clock.instant();
        long resume = Math.max(current == null ? 0 : current.resumeSeconds(), Math.clamp(command.resumeSeconds(), 0, MAX_RESUME_SECONDS));
        Instant completedAt = command.completed() ? (current != null && current.completed() ? current.completedAt() : now) : null;
        ProgressView saved = new ProgressView(lessonId, completedAt != null, completedAt, resume, revision + 1, now);
        progress.upsert(enrollmentId, saved);
        observers.forEach(observer -> observer.progressChanged(enrollmentId));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public LessonSummary summary(UUID enrollmentId) {
        EnrollmentView enrollment = enrollments.require(enrollmentId);
        return summary(catalog.requirePublished(enrollment.courseVersionId()), progress.forEnrollment(enrollmentId));
    }

    LessonSummary summary(PublishedCourse course, List<ProgressView> records) {
        Set<UUID> required = course.requiredLessonIds();
        int completed = (int) records.stream().filter(ProgressView::completed).filter(record -> required.contains(record.lessonId())).count();
        Instant lastActivity = records.stream().map(ProgressView::lastActivityAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return new LessonSummary(required.size(), completed, lastActivity);
    }
}
