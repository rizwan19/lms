package com.saasresearcher.learning.grading.internal;

import com.saasresearcher.learning.assessments.api.AttemptFinalizedListener;
import com.saasresearcher.learning.assessments.api.AttemptResults;
import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.GradingSnapshot;
import com.saasresearcher.learning.assessments.api.GradingSnapshot.Question;
import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.classrooms.api.EnrollmentAccess;
import com.saasresearcher.learning.classrooms.api.TeachingAccess;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.grading.api.GradePublication;
import com.saasresearcher.learning.grading.api.PublishedResult;
import com.saasresearcher.learning.grading.internal.GradeRows.GradeRow;
import com.saasresearcher.learning.grading.internal.GradeRows.RevisionRow;
import com.saasresearcher.learning.grading.internal.GradeRows.RevisionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grade lifecycle (spec §12): a DRAFT revision opens when an attempt closes (objective points prefilled), teachers
 * grade every written answer, FINALIZE sums the grades and PUBLISH makes an immutable copy that students read.
 * Regrading opens a new draft with a reason while students keep the previous published result.
 */
@Service
class GradingService implements GradePublication, AttemptFinalizedListener {

    static final String GRADING_INCOMPLETE = "GRADING_INCOMPLETE";
    static final String NOT_FINALIZED = "NOT_FINALIZED";
    static final String NO_OPEN_REVISION = "NO_OPEN_REVISION";
    static final String REGRADE_OPEN = "REGRADE_OPEN";

    private final GradeRepository grades;
    private final AttemptResults attempts;
    private final TeachingAccess teaching;
    private final EnrollmentAccess enrollments;
    private final AuditLog audit;
    private final Clock clock;

    GradingService(GradeRepository grades, AttemptResults attempts, TeachingAccess teaching, EnrollmentAccess enrollments, AuditLog audit,
            Clock clock) {
        this.grades = grades;
        this.attempts = attempts;
        this.teaching = teaching;
        this.enrollments = enrollments;
        this.audit = audit;
        this.clock = clock;
    }

    record GradeCommand(long expectedRevision, BigDecimal points, String feedback) {
    }

    /** Opens revision 1; objective-only attempts under automatic publication are published at once. */
    @Override
    public void attemptFinalized(UUID attemptId, AttemptStatus status) {
        GradingSnapshot snapshot = attempts.snapshot(attemptId);
        Instant now = clock.instant();
        UUID revisionId = UUID.randomUUID();
        grades.insertRevision(revisionId, attemptId, 1, snapshot.maxScore(), null, null, now);
        snapshot.questions().forEach(question -> grades.insertGrade(revisionId, new GradeRow(question.questionId(), question.autoPoints(), ""), null, now));
        if (status == AttemptStatus.GRADED && snapshot.autoPublish()) {
            BigDecimal earned = sum(grades.grades(revisionId));
            grades.setStatus(revisionId, RevisionStatus.FINALIZED, earned, now);
            publishRevision(attemptId, revisionId, earned, snapshot.maxScore(), null, now);
        }
    }

    @Transactional
    GradeRow saveGrade(UUID attemptId, UUID questionId, GradeCommand command, Actor actor) {
        GradingSnapshot snapshot = requireGradeable(attemptId, actor);
        RevisionRow revision = openRevision(attemptId);
        Revisions.require(command.expectedRevision(), revision.version());
        Question question = snapshot.questions().stream().filter(candidate -> candidate.questionId().equals(questionId))
                .findFirst().orElseThrow(ApiException::notFound);
        String feedback = command.feedback() == null ? "" : command.feedback().trim();
        if (feedback.length() > 5000) {
            throw ApiException.invalid("feedback", "Feedback can be at most 5000 characters");
        }
        BigDecimal points = question.type().isObjective() ? question.autoPoints() : requirePoints(command.points(), question.points());
        GradeRow grade = new GradeRow(questionId, points, feedback);
        grades.updateGrade(revision.id(), grade, actor.userId(), clock.instant());
        if (revision.status() == RevisionStatus.FINALIZED) {
            grades.setStatus(revision.id(), RevisionStatus.DRAFT, null, clock.instant());
        } else {
            grades.touch(revision.id());
        }
        return grade;
    }

    @Transactional
    RevisionRow finalizeGrade(UUID attemptId, long expectedRevision, Actor actor) {
        requireGradeable(attemptId, actor);
        RevisionRow revision = openRevision(attemptId);
        Revisions.require(expectedRevision, revision.version());
        List<GradeRow> rows = grades.grades(revision.id());
        if (rows.stream().anyMatch(row -> row.points() == null)) {
            throw ApiException.conflict(GRADING_INCOMPLETE, "Grade every written answer before finalizing (zero counts as a grade)");
        }
        grades.setStatus(revision.id(), RevisionStatus.FINALIZED, sum(rows), clock.instant());
        return grades.revisions(attemptId).stream().filter(row -> row.id().equals(revision.id())).findFirst().orElseThrow();
    }

    @Override
    @Transactional
    public PublishedResult publish(UUID attemptId, long expectedRevision, Actor actor) {
        GradingSnapshot snapshot = requireGradeable(attemptId, actor);
        enrollments.lock(snapshot.enrollmentId());
        RevisionRow revision = openRevision(attemptId);
        Revisions.require(expectedRevision, revision.version());
        if (revision.status() != RevisionStatus.FINALIZED) {
            throw ApiException.conflict(NOT_FINALIZED, "Finalize the grades before publishing");
        }
        BigDecimal previous = grades.published(attemptId).map(RevisionRow::earned).orElse(null);
        Instant now = clock.instant();
        publishRevision(attemptId, revision.id(), revision.earned(), revision.maximum(), actor.userId(), now);
        Map<String, Object> totals = new HashMap<>();
        totals.put("previous", previous == null ? "none" : previous.toPlainString());
        totals.put("published", revision.earned().toPlainString());
        audit.record(AuditEntry.success(actor.userId(), "grade.published", "attempt", attemptId, totals));
        return new PublishedResult(attemptId, revision.id(), revision.number(), revision.earned(), revision.maximum(), now);
    }

    @Transactional
    RevisionRow regrade(UUID attemptId, String reason, Actor actor) {
        requireGradeable(attemptId, actor);
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > 1000) {
            throw ApiException.invalid("reason", "Give a reason of up to 1000 characters");
        }
        List<RevisionRow> revisions = grades.lockRevisions(attemptId);
        if (revisions.stream().anyMatch(RevisionRow::isOpen)) {
            throw ApiException.conflict(REGRADE_OPEN, "A grade revision is already open");
        }
        RevisionRow published = revisions.stream().filter(row -> row.status() == RevisionStatus.PUBLISHED).findFirst()
                .orElseThrow(() -> ApiException.conflict(NOT_FINALIZED, "Only published results can be regraded"));
        UUID revisionId = UUID.randomUUID();
        Instant now = clock.instant();
        grades.insertRevision(revisionId, attemptId, revisions.size() + 1, published.maximum(), trimmed, actor.userId(), now);
        grades.publishedGrades(published.id()).forEach(grade -> grades.insertGrade(revisionId, grade, actor.userId(), now));
        audit.record(AuditEntry.success(actor.userId(), "grade.regrade-opened", "attempt", attemptId, Map.of("revision", revisions.size() + 1)));
        return grades.revisions(attemptId).getLast();
    }

    @Transactional(readOnly = true)
    GradeView view(UUID attemptId, Actor actor) {
        GradingSnapshot snapshot = requireGradeable(attemptId, actor);
        List<RevisionRow> revisions = grades.revisions(attemptId);
        RevisionRow current = revisions.stream().filter(RevisionRow::isOpen).findFirst().orElse(revisions.getLast());
        List<GradeRow> rows = current.status() == RevisionStatus.PUBLISHED ? grades.publishedGrades(current.id()) : grades.grades(current.id());
        return new GradeView(snapshot, current, rows, revisions);
    }

    record GradeView(GradingSnapshot attempt, RevisionRow revision, List<GradeRow> grades, List<RevisionRow> history) {
    }

    /** Graders are the attempt's class teachers and administrators; unrelated staff get not found. */
    private GradingSnapshot requireGradeable(UUID attemptId, Actor actor) {
        GradingSnapshot snapshot = attempts.snapshot(attemptId);
        teaching.requireClassTeacher(snapshot.classId(), actor);
        if (snapshot.status() == AttemptStatus.IN_PROGRESS) {
            throw ApiException.notFound();
        }
        return snapshot;
    }

    private RevisionRow openRevision(UUID attemptId) {
        return grades.lockRevisions(attemptId).stream().filter(RevisionRow::isOpen).findFirst()
                .orElseThrow(() -> ApiException.conflict(NO_OPEN_REVISION, "Nothing to grade; start a regrade to change a published result"));
    }

    private void publishRevision(UUID attemptId, UUID revisionId, BigDecimal earned, BigDecimal maximum, UUID publishedBy, Instant now) {
        grades.published(attemptId).ifPresent(previous -> grades.setStatus(previous.id(), RevisionStatus.SUPERSEDED, null, now));
        grades.markPublished(revisionId, publishedBy, now);
        attempts.recordPublished(attemptId, revisionId, earned, maximum, now);
    }

    private static BigDecimal requirePoints(BigDecimal points, BigDecimal maximum) {
        if (points == null || points.signum() < 0 || points.compareTo(maximum) > 0 || points.stripTrailingZeros().scale() > 2) {
            throw ApiException.invalid("points", "Give 0 to " + maximum.stripTrailingZeros().toPlainString() + " points with at most two decimals");
        }
        return points.setScale(2);
    }

    private static BigDecimal sum(List<GradeRow> rows) {
        return rows.stream().map(GradeRow::points).filter(Objects::nonNull).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
