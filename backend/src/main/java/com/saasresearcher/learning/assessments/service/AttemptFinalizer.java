package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.entity.AttemptRows.Answer;
import com.saasresearcher.learning.assessments.entity.AttemptRows.AttemptRow;
import com.saasresearcher.learning.assessments.entity.AttemptRows.FinalizedReason;
import com.saasresearcher.learning.assessments.entity.AttemptRows.SnapshotQuestion;
import com.saasresearcher.learning.assessments.repository.AttemptRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Closes an attempt exactly once from its acknowledged answers and scores objective questions. Callers hold the
 * attempt row lock; the conditional update makes a repeated call a no-op.
 */
@Component
public class AttemptFinalizer {

    private final AttemptRepository attempts;
    private final AttemptFinalizationHooks hooks;

    AttemptFinalizer(AttemptRepository attempts, AttemptFinalizationHooks hooks) {
        this.attempts = attempts;
        this.hooks = hooks;
    }

    void finalize(AttemptRow attempt, FinalizedReason reason, Instant now) {
        Map<UUID, Answer> answers = attempts.answers(attempt.id()).stream().collect(Collectors.toMap(Answer::questionId, Function.identity()));
        BigDecimal objective = BigDecimal.ZERO.setScale(2);
        boolean needsTeacher = false;
        for (SnapshotQuestion question : attempts.questions(attempt.id())) {
            BigDecimal points = ObjectiveScorer.score(question, answers.get(question.questionId()));
            if (points == null) {
                needsTeacher = true;
            } else {
                attempts.setAutoPoints(attempt.id(), question.questionId(), points);
                objective = objective.add(points);
            }
        }
        AttemptStatus status = needsTeacher ? AttemptStatus.AWAITING_GRADING : AttemptStatus.GRADED;
        attempts.finalize(attempt.id(), status, reason, objective, now);
        hooks.finalized(attempt.id(), status);
    }
}
