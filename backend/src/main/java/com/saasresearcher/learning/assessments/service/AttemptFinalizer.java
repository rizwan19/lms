package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.dto.ExamViews.QuestionContent;
import com.saasresearcher.learning.assessments.entity.AttemptAnswer;
import com.saasresearcher.learning.assessments.entity.ExamAttempt;
import com.saasresearcher.learning.assessments.repository.AttemptAnswerRepository;
import com.saasresearcher.learning.assessments.repository.ExamAttemptRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttemptFinalizer {

    private final ExamAttemptRepository examAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final ExamContentStore examContentStore;
    private final AttemptFinalizationHooks attemptFinalizationHooks;

    void finalize(ExamAttempt attempt, Instant now) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return;
        }
        Map<UUID, AttemptAnswer> saved = attemptAnswerRepository.findByAttemptId(attempt.getId()).stream()
                .collect(Collectors.toMap(AttemptAnswer::getQuestionId, Function.identity()));
        BigDecimal objective = BigDecimal.ZERO.setScale(2);
        boolean needsAuthor = false;
        for (QuestionContent question : examContentStore.getQuestionContents(attempt.getExamId())) {
            BigDecimal points = ObjectiveScorer.score(question, saved.get(question.id()));
            if (points == null) {
                needsAuthor = true;
            } else {
                objective = objective.add(points);
            }
        }
        AttemptStatus status = attempt.isExpired() ? AttemptStatus.EXPIRED
                : needsAuthor ? AttemptStatus.AWAITING_GRADING : AttemptStatus.GRADED;
        attempt.finish(status, objective, now);
        examAttemptRepository.flush();
        attemptFinalizationHooks.finalized(attempt.getId(), needsAuthor);
    }
}
