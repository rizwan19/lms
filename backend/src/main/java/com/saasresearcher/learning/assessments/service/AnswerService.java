package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.assessments.api.AnswerSaveRequest;
import com.saasresearcher.learning.assessments.api.AnswerSaved;
import com.saasresearcher.learning.assessments.api.AttemptOutcome;
import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.entity.AttemptAnswer;
import com.saasresearcher.learning.assessments.dto.ExamViews.OptionContent;
import com.saasresearcher.learning.assessments.dto.ExamViews.QuestionContent;
import com.saasresearcher.learning.assessments.entity.ExamAttempt;
import com.saasresearcher.learning.assessments.repository.AttemptAnswerRepository;
import com.saasresearcher.learning.assessments.repository.ExamAttemptRepository;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.enrollment.api.EnrollmentAccessService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AnswerService {

    static final String ATTEMPT_EXPIRED = "ATTEMPT_EXPIRED";
    static final String ATTEMPT_CLOSED = "ATTEMPT_CLOSED";
    static final int MAX_WRITTEN = 20_000;

    private final ExamAttemptRepository examAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final ExamContentStore examContentStore;
    private final AttemptFinalizer attemptFinalizer;
    private final EnrollmentAccessService enrollmentAccessService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final RowLocks locks;

    @Transactional(noRollbackFor = ApiException.class)
    public AnswerSaved saveAnswer(UUID attemptId, UUID questionId, AnswerSaveRequest request, Actor actor) {
        ExamAttempt examAttempt =examAttemptRepository.findById(attemptId).filter(attempt -> attempt.getLearnerId().equals(actor.userId()))
                .orElseThrow(ApiException::notFound);
        Instant now = clock.instant();
        if (examAttempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw ApiException.conflict(ATTEMPT_CLOSED, "This attempt has been submitted");
        }
        if (examAttempt.isExpired()) {
            attemptFinalizer.finalize(examAttempt, now);
            throw ApiException.conflict(ATTEMPT_EXPIRED, "Time is up; your saved answers were submitted");
        }
        enrollmentAccessService.requireActive(examAttempt.getEnrollmentId(), actor);
        QuestionContent question = examContentStore.question(examAttempt.getExamId(), questionId).orElseThrow(ApiException::notFound);
        Validated answer = validated(question, request);
        AttemptAnswer saved = attemptAnswerRepository.findByAttemptIdAndQuestionId(attemptId, questionId).orElseGet(() -> new AttemptAnswer(attemptId, questionId));
        saved.save(answer.selectedOptionIds(), answer.writtenText(), now);
        attemptAnswerRepository.saveAndFlush(saved);
        return new AnswerSaved(questionId, now);
    }

    public AttemptOutcome submit(UUID attemptId, Actor actor) {
        return transactionTemplate.execute(status -> {
            ExamAttempt attempt = lockOwn(attemptId, actor);
            if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
                attemptFinalizer.finalize(attempt, clock.instant());
            }
            return new AttemptOutcome(attempt.getId(), attempt.getStatus(), attempt.getFinalizedAt());
        });
    }

    private ExamAttempt lockOwn(UUID attemptId, Actor actor) {
        return locks.forUpdate(lockEnrollment(attemptId, actor));
    }

    /** Locks the attempt's enrollment, which serializes answer saves with submission; the attempt itself stays unlocked. */
    private ExamAttempt lockEnrollment(UUID attemptId, Actor actor) {
        ExamAttempt unlocked = examAttemptRepository.findById(attemptId).filter(attempt -> attempt.getLearnerId().equals(actor.userId()))
                .orElseThrow(ApiException::notFound);
        enrollmentAccessService.lock(unlocked.getEnrollmentId());
        return unlocked;
    }

    private record Validated(List<UUID> selectedOptionIds, String writtenText) {
    }

    private static Validated validated(QuestionContent question, AnswerSaveRequest command) {
        Set<UUID> selected = command.selectedOptionIds() == null ? Set.of() : command.selectedOptionIds();
        if (question.type() == QuestionType.WRITTEN) {
            String text = command.writtenText() == null ? "" : command.writtenText();
            if (!selected.isEmpty() || text.length() > MAX_WRITTEN) {
                throw ApiException.invalid("writtenText", "Write at most " + MAX_WRITTEN + " characters");
            }
            return new Validated(List.of(), text);
        }
        Set<UUID> allowed = question.options().stream().map(OptionContent::id).collect(Collectors.toSet());
        boolean single = question.type() != QuestionType.MULTIPLE_SELECT;
        if (command.writtenText() != null || !allowed.containsAll(selected) || (single && selected.size() > 1)) {
            throw ApiException.invalid("selectedOptionIds", single ? "Choose one answer" : "Choose from the listed answers");
        }
        return new Validated(List.copyOf(selected), null);
    }
}
