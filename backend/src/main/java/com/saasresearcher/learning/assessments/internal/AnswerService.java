package com.saasresearcher.learning.assessments.internal;

import com.saasresearcher.learning.assessments.api.AnswerSaveCommand;
import com.saasresearcher.learning.assessments.api.AnswerSaved;
import com.saasresearcher.learning.assessments.api.AttemptOutcome;
import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.AttemptView.SavedAnswer;
import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.internal.AttemptRows.Answer;
import com.saasresearcher.learning.assessments.internal.AttemptRows.AttemptRow;
import com.saasresearcher.learning.assessments.internal.AttemptRows.FinalizedReason;
import com.saasresearcher.learning.assessments.internal.AttemptRows.SnapshotOption;
import com.saasresearcher.learning.assessments.internal.AttemptRows.SnapshotQuestion;
import com.saasresearcher.learning.classrooms.api.EnrollmentAccess;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Answer saves and submission (spec §11). Every write locks the attempt first, then checks state, the server deadline
 * and the expected revision. A deadline found during a write finalizes the attempt in a committed transaction before
 * the write is rejected, so expiry never depends on the scheduled sweep alone.
 */
@Service
class AnswerService {

    static final String ANSWER_REVISION_CONFLICT = "ANSWER_REVISION_CONFLICT";
    static final String ATTEMPT_EXPIRED = "ATTEMPT_EXPIRED";
    static final String ATTEMPT_CLOSED = "ATTEMPT_CLOSED";
    static final int MAX_WRITTEN = 20_000;

    private sealed interface SaveResult permits Saved, Rejected, Conflict {
    }

    private record Saved(AnswerSaved saved) implements SaveResult {
    }

    private record Rejected(String code, String message) implements SaveResult {
    }

    private record Conflict(SavedAnswer current) implements SaveResult {
    }

    private final AttemptRepository attempts;
    private final AttemptFinalizer finalizer;
    private final EnrollmentAccess enrollments;
    private final TransactionTemplate transactions;
    private final Clock clock;

    AnswerService(AttemptRepository attempts, AttemptFinalizer finalizer, EnrollmentAccess enrollments, TransactionTemplate transactions,
            Clock clock) {
        this.attempts = attempts;
        this.finalizer = finalizer;
        this.enrollments = enrollments;
        this.transactions = transactions;
        this.clock = clock;
    }

    AnswerSaved save(UUID attemptId, UUID questionId, AnswerSaveCommand command, Actor actor) {
        SaveResult result = transactions.execute(status -> {
            AttemptRow attempt = lockOwn(attemptId, actor);
            Instant now = clock.instant();
            if (attempt.status() != AttemptStatus.IN_PROGRESS) {
                return new Rejected(ATTEMPT_CLOSED, "This attempt has been submitted");
            }
            if (attempt.isExpired(now)) {
                finalizer.finalize(attempt, FinalizedReason.EXPIRED, now);
                return new Rejected(ATTEMPT_EXPIRED, "Time is up; your saved answers were submitted");
            }
            enrollments.requireActive(attempt.enrollmentId(), actor);
            SnapshotQuestion question = attempts.questions(attemptId).stream()
                    .filter(candidate -> candidate.questionId().equals(questionId)).findFirst().orElseThrow(ApiException::notFound);
            Answer answer = validated(question, command);
            Answer current = attempts.answer(attemptId, questionId).orElse(null);
            if (command.expectedRevision() != (current == null ? 0 : current.revision())) {
                return new Conflict(current == null ? null
                        : new SavedAnswer(questionId, current.selectedOptionIds(), current.writtenText(), current.revision(), current.savedAt()));
            }
            Answer saved = new Answer(questionId, answer.selectedOptionIds(), answer.writtenText(), command.expectedRevision() + 1, now);
            attempts.saveAnswer(attemptId, saved);
            return new Saved(new AnswerSaved(questionId, saved.revision(), now));
        });
        return switch (result) {
            case Saved saved -> saved.saved();
            case Rejected rejected -> throw ApiException.conflict(rejected.code(), rejected.message());
            case Conflict conflict -> throw ApiException.conflict(ANSWER_REVISION_CONFLICT, "This answer was saved from another tab")
                    .with("current", conflict.current());
        };
    }

    /** Idempotent: a finalized attempt returns its existing outcome without rescoring. */
    AttemptOutcome submit(UUID attemptId, Actor actor) {
        return transactions.execute(status -> {
            AttemptRow attempt = lockOwn(attemptId, actor);
            if (attempt.status() == AttemptStatus.IN_PROGRESS) {
                Instant now = clock.instant();
                finalizer.finalize(attempt, attempt.isExpired(now) ? FinalizedReason.EXPIRED : FinalizedReason.SUBMITTED, now);
                attempt = attempts.find(attemptId).orElseThrow();
            }
            return new AttemptOutcome(attempt.id(), attempt.status(), attempt.finalizedReason().name(), attempt.finalizedAt());
        });
    }

    private AttemptRow lockOwn(UUID attemptId, Actor actor) {
        return attempts.lock(attemptId).filter(attempt -> attempt.studentId().equals(actor.userId())).orElseThrow(ApiException::notFound);
    }

    private static Answer validated(SnapshotQuestion question, AnswerSaveCommand command) {
        Set<UUID> selected = command.selectedOptionIds() == null ? Set.of() : command.selectedOptionIds();
        if (question.type() == QuestionType.WRITTEN) {
            String text = command.writtenText() == null ? "" : command.writtenText();
            if (!selected.isEmpty() || text.length() > MAX_WRITTEN) {
                throw ApiException.invalid("writtenText", "Write at most " + MAX_WRITTEN + " characters");
            }
            return new Answer(question.questionId(), List.of(), text, 0, null);
        }
        Set<UUID> allowed = question.options().stream().map(SnapshotOption::id).collect(Collectors.toSet());
        boolean single = question.type() != QuestionType.MULTIPLE_SELECT;
        if (command.writtenText() != null || !allowed.containsAll(selected) || (single && selected.size() > 1)) {
            throw ApiException.invalid("selectedOptionIds", single ? "Choose one answer" : "Choose from the listed answers");
        }
        return new Answer(question.questionId(), List.copyOf(selected), null, 0, null);
    }
}
