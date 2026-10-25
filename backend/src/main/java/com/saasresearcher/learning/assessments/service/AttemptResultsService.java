package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.AssessmentResults;
import com.saasresearcher.learning.assessments.api.AttemptResults;
import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.GradingSnapshot;
import com.saasresearcher.learning.assessments.api.GradingSnapshot.Option;
import com.saasresearcher.learning.assessments.api.GradingSnapshot.Question;
import com.saasresearcher.learning.assessments.api.PublishedResultListener;
import com.saasresearcher.learning.assessments.api.ResultPolicy;
import com.saasresearcher.learning.assessments.entity.AttemptAnswer;
import com.saasresearcher.learning.assessments.dto.ExamViews.QuestionContent;
import com.saasresearcher.learning.assessments.entity.Exam;
import com.saasresearcher.learning.assessments.entity.ExamAttempt;
import com.saasresearcher.learning.assessments.repository.AttemptAnswerRepository;
import com.saasresearcher.learning.assessments.repository.ExamAttemptRepository;
import com.saasresearcher.learning.assessments.repository.ExamRepository;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.enrollment.api.EnrollmentAccessService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttemptResultsService implements AttemptResults, AssessmentResults {

    private final ExamAttemptRepository attempts;
    private final AttemptAnswerRepository answers;
    private final ExamRepository exams;
    private final ExamContentStore content;
    private final EnrollmentAccessService enrollments;
    private final ObjectProvider<PublishedResultListener> listeners;
    private final Clock clock;

    AttemptResultsService(ExamAttemptRepository attempts, AttemptAnswerRepository answers,
            ExamRepository exams, ExamContentStore content, EnrollmentAccessService enrollments,
            ObjectProvider<PublishedResultListener> listeners, Clock clock) {
        this.attempts = attempts;
        this.answers = answers;
        this.exams = exams;
        this.content = content;
        this.enrollments = enrollments;
        this.listeners = listeners;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public GradingSnapshot snapshot(UUID attemptId) {
        ExamAttempt attempt = attempts.findById(attemptId).orElseThrow(ApiException::notFound);
        Exam exam = exams.findById(attempt.getExamId()).orElseThrow();
        Map<UUID, AttemptAnswer> saved = answers.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(AttemptAnswer::getQuestionId, Function.identity()));
        boolean finalized = attempt.getStatus() != AttemptStatus.IN_PROGRESS;
        List<QuestionContent> questions = content.getQuestionContents(exam.getId());
        List<Question> snapshot = IntStream.range(0, questions.size()).mapToObj(position -> {
            QuestionContent question = questions.get(position);
            AttemptAnswer answer = saved.get(question.id());
            return new Question(question.id(), position, question.type(), question.prompt(), question.points(), question.explanation(),
                    question.gradingNotes(), question.options().stream().map(option -> new Option(option.id(), option.label(), option.correct())).toList(),
                    finalized ? ObjectiveScorer.score(question, answer) : null, answer == null ? List.of() : answer.getSelectedOptionIds(),
                    answer == null ? null : answer.getWrittenText());
        }).toList();
        return new GradingSnapshot(attempt.getId(), exam.getId(), attempt.getCourseId(), attempt.getCourseVersionId(), attempt.getEnrollmentId(),
                attempt.getLearnerId(), exam.getTitle(), attempt.getAttemptNumber(), attempt.getStatus(), attempt.getFinalizedAt(), attempt.getMaxScore(),
                exam.getPassPercent(), exam.getResultPolicy() == ResultPolicy.AUTO_PUBLISH, snapshot);
    }

    @Override
    @Transactional
    public void recordPublished(UUID attemptId, UUID gradeRevisionId, BigDecimal earned, BigDecimal maximum, Instant publishedAt) {
        ExamAttempt attempt = attempts.findById(attemptId).orElseThrow();
        BigDecimal passPercent = exams.findById(attempt.getExamId()).orElseThrow().getPassPercent();
        attempt.recordPublished(gradeRevisionId, earned, maximum, passPercent, publishedAt);
        attempts.flush();
        listeners.forEach(listener -> listener.resultPublished(attempt.getEnrollmentId(), attemptId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean keysReleased(UUID attemptId) {
        ExamAttempt attempt = attempts.findById(attemptId).orElseThrow(ApiException::notFound);
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            return false;
        }
        Exam exam = exams.findById(attempt.getExamId()).orElseThrow();
        Instant now = clock.instant();
        return switch (exam.getFeedbackPolicy()) {
            case SCORE_AND_COMMENTS -> false;
            case AFTER_SUBMISSION -> true;
            case AFTER_CLOSE -> exam.hasClosed(now) && attempts.findByEnrollmentIdAndExamIdAndStatus(attempt.getEnrollmentId(),
                    attempt.getExamId(), AttemptStatus.IN_PROGRESS).filter(active -> !active.isExpired()).isEmpty();
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnResult> resultsOf(UUID learnerId) {
        Map<UUID, Exam> byId = new HashMap<>();
        return attempts.finalizedOf(learnerId).stream().map(attempt -> {
            Exam exam = byId.computeIfAbsent(attempt.getExamId(), id -> exams.findById(id).orElseThrow());
            return new OwnResult(attempt.getId(), attempt.getEnrollmentId(), attempt.getCourseId(), exam.getTitle(), exam.isRequired(), attempt.getAttemptNumber(),
                    attempt.getStatus(), attempt.getFinalizedAt(), attempt.getPublishedEarned(), attempt.getPublishedMaximum(), attempt.getPublishedPassed(),
                    attempt.getPublishedAt());
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequiredExamResult> requiredResults(UUID enrollmentId) {
        UUID versionId = enrollments.require(enrollmentId).courseVersionId();
        return exams.findByCourseVersionIdOrderByCreatedAtAscIdAsc(versionId).stream()
                .filter(Exam::isRequired)
                .map(exam -> new RequiredExamResult(exam.getId(), exam.getTitle(),
                        attempts.existsByEnrollmentIdAndExamIdAndPublishedPassedTrue(enrollmentId, exam.getId()),
                        attempts.anyUnpublished(enrollmentId, exam.getId())))
                .toList();
    }
}
