package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.dto.ExamViews.OptionContent;
import com.saasresearcher.learning.assessments.dto.ExamViews.QuestionContent;
import com.saasresearcher.learning.assessments.entity.AttemptAnswer;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ObjectiveScorer {

    private ObjectiveScorer() {
    }

    static BigDecimal score(QuestionContent question, AttemptAnswer answer) {
        if (!question.type().isObjective()) {
            return null;
        }
        Set<UUID> correct = question.options().stream().filter(OptionContent::correct).map(OptionContent::id).collect(Collectors.toSet());
        Set<UUID> selected = answer == null ? Set.of() : Set.copyOf(answer.getSelectedOptionIds());
        return !correct.isEmpty() && correct.equals(selected) ? question.points() : BigDecimal.ZERO.setScale(2);
    }
}
