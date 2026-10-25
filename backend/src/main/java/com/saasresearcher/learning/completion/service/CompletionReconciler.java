package com.saasresearcher.learning.completion.service;

import com.saasresearcher.learning.assessments.api.AssessmentResults.RequiredExamResult;
import com.saasresearcher.learning.assessments.api.AssessmentResults;
import com.saasresearcher.learning.assessments.api.PublishedResultListener;
import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.enrollment.api.EnrollmentAccessService;
import com.saasresearcher.learning.enrollment.api.EnrollmentView;
import com.saasresearcher.learning.completion.entity.CourseCompletion;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.completion.api.CompletionListener;
import com.saasresearcher.learning.completion.api.CompletionService;
import com.saasresearcher.learning.completion.api.CompletionSnapshot;
import com.saasresearcher.learning.completion.api.CompletionState;
import com.saasresearcher.learning.completion.api.CompletionStatus;
import com.saasresearcher.learning.completion.repository.CompletionRepository;
import com.saasresearcher.learning.learning.api.LearningProgress;
import com.saasresearcher.learning.learning.api.LessonSummary;
import com.saasresearcher.learning.learning.api.ProgressObserver;
import com.saasresearcher.learning.notifications.api.DurableJobs;
import com.saasresearcher.learning.notifications.api.JobRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course completion: every required lesson of the pinned edition completed and every required exam passed by a published
 * result. Reconciliation locks the enrollment, keeps exactly one completion per enrollment and is idempotent.
 * Progress and grade publication only queue reconciliation; the job does the work.
 */
@Service
public class CompletionReconciler implements CompletionService, ProgressObserver, PublishedResultListener {

    public static final String JOB_TYPE = "completion.reconcile";

    private final CompletionRepository completions;
    private final EnrollmentAccessService enrollments;
    private final LearningProgress learning;
    private final AssessmentResults assessments;
    private final DurableJobs jobs;
    private final ObjectProvider<CompletionListener> listeners;
    private final AuditService audit;
    private final Clock clock;

    CompletionReconciler(CompletionRepository completions, EnrollmentAccessService enrollments, LearningProgress learning, AssessmentResults assessments,
                         DurableJobs jobs, ObjectProvider<CompletionListener> listeners, AuditService audit, Clock clock) {
        this.completions = completions;
        this.enrollments = enrollments;
        this.learning = learning;
        this.assessments = assessments;
        this.jobs = jobs;
        this.listeners = listeners;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public void progressChanged(UUID enrollmentId) {
        queue(enrollmentId);
    }

    @Override
    public void resultPublished(UUID enrollmentId, UUID attemptId) {
        queue(enrollmentId);
    }

    @Override
    @Transactional
    public CompletionState reconcile(UUID enrollmentId) {
        EnrollmentView enrollment = enrollments.lock(enrollmentId);
        LessonSummary lessons = learning.summary(enrollmentId);
        List<RequiredExamResult> exams = assessments.requiredResults(enrollmentId);
        boolean eligible = lessons.completed() >= lessons.required() && exams.stream().allMatch(RequiredExamResult::publishedPass);
        Optional<CourseCompletion> existing = completions.findByEnrollmentId(enrollmentId);
        Instant now = clock.instant();

        if (eligible && existing.isEmpty()) {
            CourseCompletion created = completions.saveAndFlush(new CourseCompletion(enrollmentId, enrollment.learnerId(), enrollment.courseId(),
                    enrollment.courseVersionId(), now));
            enrollments.lockLearning(enrollmentId);
            audit.record(AuditEntry.success(null, "completion.achieved", "enrollment", enrollmentId));
            listeners.forEach(listener -> listener.achieved(created.getId()));
            return new CompletionState(enrollmentId, CompletionStatus.ACHIEVED, created.getId(), lessons, exams);
        }
        if (existing.isEmpty()) {
            return new CompletionState(enrollmentId, CompletionStatus.NOT_ELIGIBLE, null, lessons, exams);
        }
        CourseCompletion completion = existing.get();
        CompletionStatus target = eligible ? CompletionStatus.ACHIEVED : CompletionStatus.INVALIDATED;
        if (completion.getStatus() != target) {
            completion.setStatus(target, now);
            completions.flush();
            audit.record(AuditEntry.success(null, eligible ? "completion.restored" : "completion.invalidated", "enrollment", enrollmentId));
            listeners.forEach(listener -> {
                if (eligible) {
                    listener.achieved(completion.getId());
                } else {
                    listener.invalidated(completion.getId());
                }
            });
        }
        return new CompletionState(enrollmentId, target, completion.getId(), lessons, exams);
    }

    @Override
    @Transactional(readOnly = true)
    public CompletionSnapshot get(UUID completionId) {
        return completions.findById(completionId).map(CourseCompletion::toSnapshot).orElseThrow(ApiException::notFound);
    }

    @Transactional(readOnly = true)
    public CompletionState state(UUID enrollmentId) {
        LessonSummary lessons = learning.summary(enrollmentId);
        List<RequiredExamResult> exams = assessments.requiredResults(enrollmentId);
        return completions.findByEnrollmentId(enrollmentId)
                .map(completion -> new CompletionState(enrollmentId, completion.getStatus(), completion.getId(), lessons, exams))
                .orElse(new CompletionState(enrollmentId, CompletionStatus.NOT_ELIGIBLE, null, lessons, exams));
    }

    /** At-least-once and idempotent, so no deduplication key is needed. */
    private void queue(UUID enrollmentId) {
        jobs.enqueue(JobRequest.now(JOB_TYPE, enrollmentId, null, clock.instant()));
    }
}
