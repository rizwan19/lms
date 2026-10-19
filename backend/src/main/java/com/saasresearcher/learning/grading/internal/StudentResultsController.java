package com.saasresearcher.learning.grading.internal;

import com.saasresearcher.learning.assessments.api.AttemptResults;
import com.saasresearcher.learning.assessments.api.GradingSnapshot;
import com.saasresearcher.learning.assessments.api.GradingSnapshot.Option;
import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.grading.internal.GradeRows.GradeRow;
import com.saasresearcher.learning.grading.internal.GradeRows.RevisionRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A student's own released results. Only published revisions are shown; pending grading appears as pending, never as
 * zero. Keys and explanations appear only when the assignment's feedback policy releases them.
 */
@RestController
class StudentResultsController {

    private final AttemptResults attempts;
    private final GradeRepository grades;

    StudentResultsController(AttemptResults attempts, GradeRepository grades) {
        this.attempts = attempts;
        this.grades = grades;
    }

    @GetMapping("/me/results")
    @Transactional(readOnly = true)
    List<ResultSummary> results(Actor actor) {
        return attempts.resultsOf(actor.userId()).stream()
                .map(result -> new ResultSummary(result.attemptId(), result.enrollmentId(), result.examTitle(), result.required(), result.attemptNumber(),
                        result.finalizedAt(), result.publishedAt() == null ? "PENDING" : "PUBLISHED", result.publishedEarned(), result.publishedMaximum(),
                        result.publishedPassed(), result.publishedAt()))
                .toList();
    }

    @GetMapping("/me/results/{attemptId}")
    @Transactional(readOnly = true)
    ResultDetail result(@PathVariable UUID attemptId, Actor actor) {
        GradingSnapshot snapshot = attempts.snapshot(attemptId);
        if (!snapshot.studentId().equals(actor.userId())) {
            throw ApiException.notFound();
        }
        RevisionRow published = grades.published(attemptId).orElseThrow(ApiException::notFound);
        Map<UUID, GradeRow> grade = grades.publishedGrades(published.id()).stream().collect(Collectors.toMap(GradeRow::questionId, Function.identity()));
        boolean keys = attempts.keysReleased(attemptId);
        List<QuestionResult> questions = snapshot.questions().stream().map(question -> {
            Map<UUID, String> labels = question.options().stream().collect(Collectors.toMap(Option::id, Option::label));
            return new QuestionResult(question.position(), question.type(), question.prompt(), question.points(),
                    grade.get(question.questionId()).points(), grade.get(question.questionId()).feedback(),
                    question.selectedOptionIds().stream().map(labels::get).toList(), question.writtenText(),
                    keys ? question.options().stream().filter(Option::correct).map(Option::label).toList() : null,
                    keys ? question.explanation() : null);
        }).toList();
        return new ResultDetail(attemptId, snapshot.examTitle(), snapshot.attemptNumber(), published.earned(), published.maximum(),
                published.publishedAt(), keys, questions);
    }

    record ResultSummary(UUID attemptId, UUID enrollmentId, String examTitle, boolean required, int attemptNumber, Instant finalizedAt, String state,
            BigDecimal earned, BigDecimal maximum, Boolean passed, Instant publishedAt) {
    }

    record QuestionResult(int position, QuestionType type, String prompt, BigDecimal maxPoints, BigDecimal points, String feedback,
            List<String> yourChoices, String yourText, List<String> correctAnswers, String explanation) {
    }

    record ResultDetail(UUID attemptId, String examTitle, int attemptNumber, BigDecimal earned, BigDecimal maximum, Instant publishedAt,
            boolean answersReleased, List<QuestionResult> questions) {
    }
}
