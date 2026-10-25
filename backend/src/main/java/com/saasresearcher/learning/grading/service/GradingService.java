package com.saasresearcher.learning.grading.service;

import com.saasresearcher.learning.assessments.api.AttemptFinalizedListener;
import com.saasresearcher.learning.assessments.api.AttemptResults;
import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.GradingSnapshot.Question;
import com.saasresearcher.learning.assessments.api.GradingSnapshot;
import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.courses.api.CourseOwnershipService;
import com.saasresearcher.learning.enrollment.api.EnrollmentAccessService;
import com.saasresearcher.learning.grading.api.GradePublication;
import com.saasresearcher.learning.grading.api.PublishedResult;
import com.saasresearcher.learning.grading.api.RevisionStatus;
import com.saasresearcher.learning.grading.dto.GradeViews.GradeCommand;
import com.saasresearcher.learning.grading.dto.GradeViews.GradeRow;
import com.saasresearcher.learning.grading.dto.GradeViews.GradeView;
import com.saasresearcher.learning.grading.dto.GradeViews.RevisionView;
import com.saasresearcher.learning.grading.entity.AnswerGrade;
import com.saasresearcher.learning.grading.entity.GradeRevision;
import com.saasresearcher.learning.grading.entity.PublishedQuestionGrade;
import com.saasresearcher.learning.grading.repository.AnswerGradeRepository;
import com.saasresearcher.learning.grading.repository.GradeRevisionRepository;
import com.saasresearcher.learning.grading.repository.PublishedQuestionGradeRepository;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grade lifecycle: a DRAFT revision opens when an attempt closes (objective points prefilled), the course author grades
 * every written answer, FINALIZE sums the grades and PUBLISH makes an immutable copy that learners read. Regrading opens
 * a new draft with a reason while learners keep the previous published result. Only the course's author grades it;
 * teacher approval or workspace administration alone never grants access.
 */
@Service
public class GradingService implements GradePublication, AttemptFinalizedListener {

    static final String GRADING_INCOMPLETE = "GRADING_INCOMPLETE";
    static final String NOT_FINALIZED = "NOT_FINALIZED";
    static final String NO_OPEN_REVISION = "NO_OPEN_REVISION";
    static final String REGRADE_OPEN = "REGRADE_OPEN";

    private final GradeRevisionRepository revisions;
    private final AnswerGradeRepository grades;
    private final PublishedQuestionGradeRepository publishedGrades;
    private final AttemptResults attempts;
    private final CourseOwnershipService ownership;
    private final IdentityAccess identity;
    private final EnrollmentAccessService enrollments;
    private final AuditService audit;
    private final RowLocks locks;
    private final Clock clock;

    GradingService(GradeRevisionRepository revisions, AnswerGradeRepository grades, PublishedQuestionGradeRepository publishedGrades,
                   AttemptResults attempts, CourseOwnershipService ownership, IdentityAccess identity, EnrollmentAccessService enrollments, AuditService audit,
                   RowLocks locks, Clock clock) {
        this.revisions = revisions;
        this.grades = grades;
        this.publishedGrades = publishedGrades;
        this.attempts = attempts;
        this.ownership = ownership;
        this.identity = identity;
        this.enrollments = enrollments;
        this.audit = audit;
        this.locks = locks;
        this.clock = clock;
    }

    /** Opens revision 1; objective-only attempts under automatic publication are published at once. */
    @Override
    public void attemptFinalized(UUID attemptId, boolean needsAuthor) {
        GradingSnapshot snapshot = attempts.snapshot(attemptId);
        Instant now = clock.instant();
        GradeRevision revision = revisions.saveAndFlush(new GradeRevision(attemptId, 1, snapshot.maxScore(), null, null, now));
        snapshot.questions().forEach(question -> grades.save(new AnswerGrade(revision.getId(), question.questionId(), question.autoPoints(), "", null, now)));
        grades.flush();
        if (!needsAuthor && snapshot.autoPublish()) {
            revision.finalizeWith(sum(grades.findByRevisionId(revision.getId())), now);
            publishRevision(revision, null, now);
        }
    }

    @Transactional
    public GradeRow saveGrade(UUID attemptId, UUID questionId, GradeCommand command, Actor actor) {
        GradingSnapshot snapshot = requireGradeable(attemptId, actor);
        GradeRevision revision = openRevision(attemptId);
        Revisions.require(command.expectedRevision(), revision.getVersion());
        Question question = snapshot.questions().stream().filter(candidate -> candidate.questionId().equals(questionId))
                .findFirst().orElseThrow(ApiException::notFound);
        String feedback = command.feedback() == null ? "" : command.feedback().trim();
        if (feedback.length() > 5000) {
            throw ApiException.invalid("feedback", "Feedback can be at most 5000 characters");
        }
        BigDecimal points = question.type().isObjective() ? question.autoPoints() : requirePoints(command.points(), question.points());
        AnswerGrade grade = grades.findByRevisionIdAndQuestionId(revision.getId(), questionId).orElseThrow();
        grade.grade(points, feedback, actor.userId(), clock.instant());
        locks.bumpVersion(revision);
        revision.changed();
        revisions.flush();
        return new GradeRow(questionId, points, feedback);
    }

    @Transactional
    public RevisionView finalizeGrade(UUID attemptId, long expectedRevision, Actor actor) {
        requireGradeable(attemptId, actor);
        GradeRevision revision = openRevision(attemptId);
        Revisions.require(expectedRevision, revision.getVersion());
        List<AnswerGrade> rows = grades.findByRevisionId(revision.getId());
        if (rows.stream().anyMatch(row -> row.getPoints() == null)) {
            throw ApiException.conflict(GRADING_INCOMPLETE, "Grade every written answer before finalizing (zero counts as a grade)");
        }
        revision.finalizeWith(sum(rows), clock.instant());
        revisions.flush();
        return RevisionView.of(revision);
    }

    /**
     * Publishes the finalized revision. Lock order: the author's account and the course shared, then the enrollment,
     * then the revisions; the author's approval is rechecked under those locks. The published copy, the attempt's
     * projection and the completion job commit together, so a failed publication leaks nothing.
     */
    @Override
    @Transactional
    public PublishedResult publish(UUID attemptId, long expectedRevision, Actor actor) {
        GradingSnapshot snapshot = requireGradeable(attemptId, actor);
        ownership.requirePublishAuthor(snapshot.courseId(), actor);
        AccountSummary author = identity.lockShared(List.of(actor.userId())).get(actor.userId());
        if (author == null || !author.isActiveApprovedTeacher()) {
            throw ApiException.teacherApprovalRequired();
        }
        ownership.lockShared(snapshot.courseId());
        enrollments.lock(snapshot.enrollmentId());
        GradeRevision revision = openRevision(attemptId);
        Revisions.require(expectedRevision, revision.getVersion());
        if (revision.getStatus() != RevisionStatus.FINALIZED) {
            throw ApiException.conflict(NOT_FINALIZED, "Finalize the grades before publishing");
        }
        BigDecimal previous = revisions.findByAttemptIdAndStatus(attemptId, RevisionStatus.PUBLISHED).map(GradeRevision::getEarned).orElse(null);
        Instant now = clock.instant();
        publishRevision(revision, actor.userId(), now);
        Map<String, Object> totals = new HashMap<>();
        totals.put("previous", previous == null ? "none" : previous.toPlainString());
        totals.put("published", revision.getEarned().toPlainString());
        audit.record(AuditEntry.success(actor.userId(), "grade.published", "attempt", attemptId, totals));
        return new PublishedResult(attemptId, revision.getId(), revision.getNumber(), revision.getEarned(), revision.getMaximum(), now);
    }

    @Transactional
    public RevisionView regrade(UUID attemptId, String reason, Actor actor) {
        requireGradeable(attemptId, actor);
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > 1000) {
            throw ApiException.invalid("reason", "Give a reason of up to 1000 characters");
        }
        List<GradeRevision> history = revisions.lockByAttempt(attemptId);
        if (history.stream().anyMatch(GradeRevision::isOpen)) {
            throw ApiException.conflict(REGRADE_OPEN, "A grade revision is already open");
        }
        GradeRevision published = history.stream().filter(row -> row.getStatus() == RevisionStatus.PUBLISHED).findFirst()
                .orElseThrow(() -> ApiException.conflict(NOT_FINALIZED, "Only published results can be regraded"));
        Instant now = clock.instant();
        GradeRevision draft = revisions.saveAndFlush(new GradeRevision(attemptId, history.size() + 1, published.getMaximum(), trimmed, actor.userId(), now));
        publishedGrades.findByRevisionId(published.getId()).forEach(grade -> grades.save(new AnswerGrade(draft.getId(), grade.getQuestionId(), grade.getPoints(),
                grade.getFeedback(), actor.userId(), now)));
        grades.flush();
        audit.record(AuditEntry.success(actor.userId(), "grade.regrade-opened", "attempt", attemptId, Map.of("revision", draft.getNumber())));
        return RevisionView.of(draft);
    }

    @Transactional(readOnly = true)
    public GradeView view(UUID attemptId, Actor actor) {
        GradingSnapshot snapshot = requireGradeable(attemptId, actor);
        List<GradeRevision> history = revisions.findByAttemptIdOrderByNumber(attemptId);
        GradeRevision current = history.stream().filter(GradeRevision::isOpen).findFirst().orElse(history.getLast());
        List<GradeRow> rows = current.getStatus() == RevisionStatus.PUBLISHED ? publishedGrades(current.getId())
                : grades.findByRevisionId(current.getId()).stream().map(grade -> new GradeRow(grade.getQuestionId(), grade.getPoints(), grade.getFeedback())).toList();
        return new GradeView(snapshot, RevisionView.of(current), rows, history.stream().map(RevisionView::of).toList());
    }

    @Transactional(readOnly = true)
    public Optional<RevisionView> published(UUID attemptId) {
        return revisions.findByAttemptIdAndStatus(attemptId, RevisionStatus.PUBLISHED).map(RevisionView::of);
    }

    @Transactional(readOnly = true)
    public List<GradeRow> publishedGrades(UUID revisionId) {
        return publishedGrades.findByRevisionId(revisionId).stream().map(grade -> new GradeRow(grade.getQuestionId(), grade.getPoints(), grade.getFeedback()))
                .toList();
    }

    /** Graders are the course's author only; anyone else gets not found. Attempts in progress cannot be graded. */
    private GradingSnapshot requireGradeable(UUID attemptId, Actor actor) {
        GradingSnapshot snapshot = attempts.snapshot(attemptId);
        ownership.requireAuthor(snapshot.courseId(), actor);
        if (snapshot.status() == AttemptStatus.IN_PROGRESS) {
            throw ApiException.notFound();
        }
        return snapshot;
    }

    private GradeRevision openRevision(UUID attemptId) {
        return revisions.lockByAttempt(attemptId).stream().filter(GradeRevision::isOpen).findFirst()
                .orElseThrow(() -> ApiException.conflict(NO_OPEN_REVISION, "Nothing to grade; start a regrade to change a published result"));
    }

    private void publishRevision(GradeRevision revision, UUID publishedBy, Instant now) {
        revisions.findByAttemptIdAndStatus(revision.getAttemptId(), RevisionStatus.PUBLISHED).ifPresent(GradeRevision::supersede);
        revisions.flush();
        revision.publish(publishedBy, now);
        revisions.flush();
        grades.findByRevisionId(revision.getId()).forEach(grade -> publishedGrades.save(new PublishedQuestionGrade(revision.getId(), grade.getQuestionId(),
                grade.getPoints(), grade.getFeedback())));
        publishedGrades.flush();
        attempts.recordPublished(revision.getAttemptId(), revision.getId(), revision.getEarned(), revision.getMaximum(), now);
    }

    private static BigDecimal requirePoints(BigDecimal points, BigDecimal maximum) {
        if (points == null || points.signum() < 0 || points.compareTo(maximum) > 0 || points.stripTrailingZeros().scale() > 2) {
            throw ApiException.invalid("points", "Give 0 to " + maximum.stripTrailingZeros().toPlainString() + " points with at most two decimals");
        }
        return points.setScale(2);
    }

    private static BigDecimal sum(List<AnswerGrade> rows) {
        return rows.stream().map(AnswerGrade::getPoints).filter(Objects::nonNull).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
