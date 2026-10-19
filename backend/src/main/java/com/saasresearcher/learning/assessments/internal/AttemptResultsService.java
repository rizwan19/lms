package com.saasresearcher.learning.assessments.internal;

import com.saasresearcher.learning.assessments.api.AssessmentResults;
import com.saasresearcher.learning.assessments.api.AttemptResults;
import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.GradingSnapshot;
import com.saasresearcher.learning.assessments.api.GradingSnapshot.Option;
import com.saasresearcher.learning.assessments.api.GradingSnapshot.Question;
import com.saasresearcher.learning.assessments.api.PublishedResultListener;
import com.saasresearcher.learning.assessments.internal.AttemptRows.Answer;
import com.saasresearcher.learning.assessments.internal.AttemptRows.AttemptRow;
import com.saasresearcher.learning.assessments.internal.ExamRows.VersionRow;
import com.saasresearcher.learning.assessments.internal.Schedule.ResultPolicy;
import com.saasresearcher.learning.classrooms.api.EnrollmentAccess;
import com.saasresearcher.learning.common.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AttemptResultsService implements AttemptResults, AssessmentResults {

    private final AttemptRepository attempts;
    private final AssignmentRepository assignments;
    private final ExamRepository exams;
    private final EnrollmentAccess enrollments;
    private final ObjectProvider<PublishedResultListener> listeners;
    private final Clock clock;

    AttemptResultsService(AttemptRepository attempts, AssignmentRepository assignments, ExamRepository exams, EnrollmentAccess enrollments,
            ObjectProvider<PublishedResultListener> listeners, Clock clock) {
        this.attempts = attempts;
        this.assignments = assignments;
        this.exams = exams;
        this.enrollments = enrollments;
        this.listeners = listeners;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public GradingSnapshot snapshot(UUID attemptId) {
        AttemptRow attempt = attempts.find(attemptId).orElseThrow(ApiException::notFound);
        AssignmentRow assignment = assignments.find(attempt.assignmentId()).orElseThrow();
        VersionRow exam = exams.findVersion(assignment.examVersionId()).orElseThrow();
        Map<UUID, Answer> answers = attempts.answers(attemptId).stream().collect(Collectors.toMap(Answer::questionId, Function.identity()));
        List<Question> questions = attempts.questions(attemptId).stream().map(question -> {
            Answer answer = answers.get(question.questionId());
            return new Question(question.questionId(), question.position(), question.type(), question.prompt(), question.points(),
                    question.explanation(), question.gradingNotes(),
                    question.options().stream().map(option -> new Option(option.id(), option.label(), option.correct())).toList(),
                    question.autoPoints(), answer == null ? List.of() : answer.selectedOptionIds(), answer == null ? null : answer.writtenText());
        }).toList();
        return new GradingSnapshot(attempt.id(), assignment.id(), assignment.classId(), attempt.enrollmentId(), attempt.studentId(), exam.title(),
                attempt.attemptNumber(), attempt.status(), attempt.finalizedAt(), attempt.maxScore(), exam.passPercent(),
                assignment.schedule().resultPolicy() == ResultPolicy.AUTO_PUBLISH, questions);
    }

    @Override
    @Transactional
    public void recordPublished(UUID attemptId, UUID gradeRevisionId, BigDecimal earned, BigDecimal maximum, Instant publishedAt) {
        AttemptRow attempt = attempts.find(attemptId).orElseThrow();
        AssignmentRow assignment = assignments.find(attempt.assignmentId()).orElseThrow();
        BigDecimal passPercent = exams.findVersion(assignment.examVersionId()).orElseThrow().passPercent();
        attempts.recordPublished(attemptId, gradeRevisionId, earned, maximum, passPercent, publishedAt);
        listeners.forEach(listener -> listener.resultPublished(attempt.enrollmentId(), attemptId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean keysReleased(UUID attemptId) {
        AttemptRow attempt = attempts.find(attemptId).orElseThrow(ApiException::notFound);
        if (attempt.status() == AttemptStatus.IN_PROGRESS) {
            return false;
        }
        Schedule schedule = assignments.find(attempt.assignmentId()).orElseThrow().schedule();
        Instant now = clock.instant();
        return switch (schedule.feedbackPolicy()) {
            case SCORE_AND_COMMENTS -> false;
            case AFTER_SUBMISSION -> true;
            case AFTER_CLOSE -> !now.isBefore(schedule.closesAt())
                    && attempts.findActive(attempt.enrollmentId(), attempt.assignmentId()).filter(active -> !active.isExpired(now)).isEmpty();
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnResult> resultsOf(UUID studentId) {
        return attempts.resultsOf(studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequiredExamResult> requiredResults(UUID enrollmentId) {
        UUID classId = enrollments.require(enrollmentId).classId();
        return assignments.forClass(classId).stream()
                .filter(AssignmentRow::required)
                .map(assignment -> new RequiredExamResult(assignment.id(), attempts.anyPublishedPass(enrollmentId, assignment.id())))
                .toList();
    }
}
