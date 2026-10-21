package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.entity.AttemptRows.Answer;
import com.saasresearcher.learning.assessments.entity.AttemptRows.SnapshotOption;
import com.saasresearcher.learning.assessments.entity.AttemptRows.SnapshotQuestion;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Objective scoring (spec §11): full points only when the selected set equals the correct set exactly; no partial or
 * negative marks; unanswered questions earn zero. Written questions are never auto-scored.
 */
public final class ObjectiveScorer {

    private ObjectiveScorer() {
    }

    /** Points earned, or null for written questions that need a teacher. */
    static BigDecimal score(SnapshotQuestion question, Answer answer) {
        if (!question.type().isObjective()) {
            return null;
        }
        Set<UUID> correct = question.options().stream().filter(SnapshotOption::correct).map(SnapshotOption::id).collect(Collectors.toSet());
        Set<UUID> selected = answer == null ? Set.of() : Set.copyOf(answer.selectedOptionIds());
        return !correct.isEmpty() && correct.equals(selected) ? question.points() : BigDecimal.ZERO.setScale(2);
    }
}
