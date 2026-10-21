package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.AttemptService;
import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.AttemptView.OptionView;
import com.saasresearcher.learning.assessments.api.AttemptView.QuestionView;
import com.saasresearcher.learning.assessments.api.AttemptView.SavedAnswer;
import com.saasresearcher.learning.assessments.api.AttemptView;
import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.entity.AssignmentRow;
import com.saasresearcher.learning.assessments.entity.AttemptRows.AttemptRow;
import com.saasresearcher.learning.assessments.entity.AttemptRows.FinalizedReason;
import com.saasresearcher.learning.assessments.entity.AttemptRows.SnapshotOption;
import com.saasresearcher.learning.assessments.entity.AttemptRows.SnapshotQuestion;
import com.saasresearcher.learning.assessments.entity.ExamQuestion;
import com.saasresearcher.learning.assessments.entity.ExamRows.VersionRow;
import com.saasresearcher.learning.assessments.entity.Schedule;
import com.saasresearcher.learning.assessments.repository.AssignmentRepository;
import com.saasresearcher.learning.assessments.repository.AttemptRepository;
import com.saasresearcher.learning.assessments.repository.ExamContentRepository;
import com.saasresearcher.learning.assessments.repository.ExamRepository;
import com.saasresearcher.learning.classrooms.api.ClassDirectory;
import com.saasresearcher.learning.classrooms.api.EnrollmentAccess;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts attempts under the enrollment lock (spec §11): checks access, schedule and attempt count, returns an attempt
 * already in progress, fixes the deadline and snapshots a securely shuffled copy of the exam edition.
 */
@Service
public class AttemptStartService implements AttemptService {

    static final String EXAM_NOT_OPEN = "EXAM_NOT_OPEN";
    static final String ATTEMPT_LIMIT_REACHED = "ATTEMPT_LIMIT_REACHED";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AttemptRepository attempts;
    private final AssignmentRepository assignments;
    private final ExamRepository exams;
    private final ExamContentRepository content;
    private final ClassDirectory classes;
    private final EnrollmentAccess enrollments;
    private final AttemptFinalizer finalizer;
    private final Clock clock;

    AttemptStartService(AttemptRepository attempts, AssignmentRepository assignments, ExamRepository exams, ExamContentRepository content,
            ClassDirectory classes, EnrollmentAccess enrollments, AttemptFinalizer finalizer, Clock clock) {
        this.attempts = attempts;
        this.assignments = assignments;
        this.exams = exams;
        this.content = content;
        this.classes = classes;
        this.enrollments = enrollments;
        this.finalizer = finalizer;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AttemptView start(UUID assignmentId, Actor actor) {
        AssignmentRow assignment = assignments.find(assignmentId).orElseThrow(ApiException::notFound);
        UUID enrollmentId = classes.enrollmentOf(assignment.classId(), actor.userId()).orElseThrow(ApiException::notFound);
        enrollments.lockActive(enrollmentId, actor);
        Instant now = clock.instant();

        Optional<AttemptRow> active = attempts.findActive(enrollmentId, assignmentId);
        if (active.isPresent() && !active.get().isExpired(now)) {
            return view(active.get(), assignment, now);
        }
        active.ifPresent(expired -> finalizer.finalize(attempts.lock(expired.id()).orElseThrow(), FinalizedReason.EXPIRED, now));
        if (!assignment.isOpen(now)) {
            throw ApiException.conflict(EXAM_NOT_OPEN, "This exam is not open right now");
        }
        int used = attempts.forEnrollment(enrollmentId, assignmentId).size();
        if (used >= assignment.schedule().maxAttempts()) {
            throw ApiException.conflict(ATTEMPT_LIMIT_REACHED, "You have used all attempts for this exam");
        }

        List<SnapshotQuestion> snapshot = snapshot(content.questions(assignment.examVersionId()));
        BigDecimal maxScore = snapshot.stream().map(SnapshotQuestion::points).reduce(BigDecimal.ZERO, BigDecimal::add);
        AttemptRow attempt = new AttemptRow(UUID.randomUUID(), assignmentId, enrollmentId, actor.userId(), used + 1, AttemptStatus.IN_PROGRESS,
                now, deadline(assignment.schedule(), now), null, null, null, maxScore);
        attempts.insert(attempt, snapshot);
        return view(attempt, assignment, now);
    }

    @Override
    @Transactional
    public AttemptView get(UUID attemptId, Actor actor) {
        AttemptRow attempt = requireOwn(attemptId, actor);
        Instant now = clock.instant();
        if (attempt.isExpired(now)) {
            finalizer.finalize(attempts.lock(attemptId).orElseThrow(), FinalizedReason.EXPIRED, now);
            attempt = attempts.find(attemptId).orElseThrow();
        }
        return view(attempt, assignments.find(attempt.assignmentId()).orElseThrow(), now);
    }

    AttemptRow requireOwn(UUID attemptId, Actor actor) {
        return attempts.find(attemptId).filter(attempt -> attempt.studentId().equals(actor.userId())).orElseThrow(ApiException::notFound);
    }

    /** The earlier of start plus duration and the assignment's closing time. */
    static Instant deadline(Schedule schedule, Instant now) {
        Instant byDuration = schedule.durationMinutes() == null ? null : now.plus(Duration.ofMinutes(schedule.durationMinutes()));
        if (byDuration == null) {
            return schedule.closesAt();
        }
        return schedule.closesAt() == null || byDuration.isBefore(schedule.closesAt()) ? byDuration : schedule.closesAt();
    }

    private static List<SnapshotQuestion> snapshot(List<ExamQuestion> questions) {
        List<ExamQuestion> order = new ArrayList<>(questions);
        Collections.shuffle(order, RANDOM);
        return IntStream.range(0, order.size()).mapToObj(position -> {
            ExamQuestion question = order.get(position);
            List<SnapshotOption> options = new ArrayList<>(question.options().stream()
                    .map(option -> new SnapshotOption(option.id(), option.label(), option.correct())).toList());
            if (question.type() != QuestionType.TRUE_FALSE) {
                Collections.shuffle(options, RANDOM);
            }
            return new SnapshotQuestion(question.id(), position, question.type(), question.prompt(), question.points(), question.explanation(),
                    question.gradingNotes(), options, null);
        }).toList();
    }

    private AttemptView view(AttemptRow attempt, AssignmentRow assignment, Instant now) {
        VersionRow exam = exams.findVersion(assignment.examVersionId()).orElseThrow();
        List<QuestionView> questions = attempts.questions(attempt.id()).stream()
                .map(question -> new QuestionView(question.questionId(), question.position(), question.type(), question.prompt(), question.points(),
                        question.options().stream().map(option -> new OptionView(option.id(), option.label())).toList()))
                .toList();
        List<SavedAnswer> answers = attempts.answers(attempt.id()).stream()
                .map(answer -> new SavedAnswer(answer.questionId(), answer.selectedOptionIds(), answer.writtenText(), answer.revision(), answer.savedAt()))
                .toList();
        return new AttemptView(attempt.id(), assignment.id(), attempt.enrollmentId(), exam.title(), exam.instructions(), attempt.attemptNumber(),
                assignment.schedule().maxAttempts(), attempt.status(), attempt.startedAt(), attempt.expiresAt(), now, attempt.finalizedAt(),
                questions, answers);
    }
}
