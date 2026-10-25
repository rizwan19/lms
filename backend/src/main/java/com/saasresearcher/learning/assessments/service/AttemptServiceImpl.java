package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.assessments.api.AttemptService;
import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.AttemptView;
import com.saasresearcher.learning.assessments.api.AttemptView.OptionView;
import com.saasresearcher.learning.assessments.api.AttemptView.LearnerQuestionView;
import com.saasresearcher.learning.assessments.api.AttemptView.SavedAnswer;
import com.saasresearcher.learning.assessments.dto.ExamViews.QuestionContent;
import com.saasresearcher.learning.assessments.entity.Exam;
import com.saasresearcher.learning.assessments.entity.ExamAttempt;
import com.saasresearcher.learning.assessments.repository.AttemptAnswerRepository;
import com.saasresearcher.learning.assessments.repository.ExamAttemptRepository;
import com.saasresearcher.learning.assessments.repository.ExamRepository;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.enrollment.api.EnrollmentAccessService;
import com.saasresearcher.learning.enrollment.api.EnrollmentView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttemptServiceImpl implements AttemptService {

    static final String EXAM_NOT_OPEN = "EXAM_NOT_OPEN";
    static final String ATTEMPT_LIMIT_REACHED = "ATTEMPT_LIMIT_REACHED";

    private final ExamAttemptRepository examAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final ExamRepository examRepository;
    private final ExamContentStore examContentStore;
    private final EnrollmentAccessService enrollmentAccessService;
    private final AttemptFinalizer attemptFinalizer;
    private final Clock clock;
    private final RowLocks locks;

    @Override
    @Transactional
    public AttemptView startAttempt(UUID examId, Actor actor) {
        Exam exam = examRepository.findById(examId).orElseThrow(ApiException::notFound);
        EnrollmentView enrollment = enrollmentAccessService.find(exam.getCourseId(), actor.userId())
                .filter(found -> found.courseVersionId().equals(exam.getCourseVersionId()))
                .orElseThrow(ApiException::notFound);
        enrollmentAccessService.requireActive(enrollment.id(), actor);
        Instant now = clock.instant();

        Optional<ExamAttempt> activeAttempts = examAttemptRepository.findByEnrollmentIdAndExamIdAndStatus(enrollment.id(), examId, AttemptStatus.IN_PROGRESS);
        if (activeAttempts.isPresent() && !activeAttempts.get().isExpired()) {
            return assembleAttemptView(activeAttempts.get(), exam, now);
        }
        activeAttempts.ifPresent(attempt -> attemptFinalizer.finalize(locks.forUpdate(attempt), now));
        if (!exam.isOpen(now)) {
            throw ApiException.conflict(EXAM_NOT_OPEN, "This exam is not open right now");
        }
        int previousAttempts = examAttemptRepository.findByEnrollmentIdAndExamIdOrderByAttemptNumber(enrollment.id(), examId).size();
        if (previousAttempts >= exam.getMaxAttempts()) {
            throw ApiException.conflict(ATTEMPT_LIMIT_REACHED, "You have used all attempts for this exam");
        }

        BigDecimal maxScore = examContentStore.summary(examId).maxPoints();
        ExamAttempt attempt = examAttemptRepository.saveAndFlush(new ExamAttempt(exam, enrollment.id(), actor.userId(), previousAttempts + 1, now, maxScore));
        return assembleAttemptView(attempt, exam, now);
    }

    @Override
    @Transactional
    public AttemptView get(UUID attemptId, Actor actor) {
        ExamAttempt attempt = examAttemptRepository.findById(attemptId).filter(found -> found.getLearnerId().equals(actor.userId())).orElseThrow(ApiException::notFound);
        Instant now = clock.instant();
        if (attempt.isExpired()) {
            attemptFinalizer.finalize(locks.forUpdate(attempt), now);
        }
        return assembleAttemptView(attempt, examRepository.findById(attempt.getExamId()).orElseThrow(), now);
    }

    private AttemptView assembleAttemptView(ExamAttempt attempt, Exam exam, Instant now) {
        List<QuestionContent> questionContents = examContentStore.getQuestionContents(exam.getId());
        List<LearnerQuestionView> learnerQuestionViews = IntStream.range(0, questionContents.size()).mapToObj(position -> {
            QuestionContent question = questionContents.get(position);
            return new LearnerQuestionView(question.id(), position, question.type(), question.prompt(), question.points(),
                    question.options().stream().map(option -> new OptionView(option.id(), option.label())).toList());
        }).toList();
        List<SavedAnswer> saved = attemptAnswerRepository.findByAttemptId(attempt.getId()).stream()
                .map(answer -> new SavedAnswer(answer.getQuestionId(), answer.getSelectedOptionIds(), answer.getWrittenText(), answer.getSavedAt()))
                .toList();
        return new AttemptView(attempt.getId(), exam.getId(), attempt.getEnrollmentId(), exam.getTitle(), exam.getInstructions(), attempt.getAttemptNumber(),
                exam.getMaxAttempts(), attempt.getStatus(), attempt.getStartedAt(), attempt.getExpiresAt(), now, attempt.getFinalizedAt(), learnerQuestionViews, saved);
    }
}
