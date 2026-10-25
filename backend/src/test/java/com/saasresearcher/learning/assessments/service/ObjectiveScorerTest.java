package com.saasresearcher.learning.assessments.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.dto.ExamViews.OptionContent;
import com.saasresearcher.learning.assessments.dto.ExamViews.QuestionContent;
import com.saasresearcher.learning.assessments.entity.AttemptAnswer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ObjectiveScorerTest {

    private final UUID questionId = UUID.randomUUID();
    private final OptionContent a = option("A", true);
    private final OptionContent b = option("B", true);
    private final OptionContent c = option("C", false);
    private final QuestionContent multiple = question(QuestionType.MULTIPLE_SELECT, "2.50", List.of(a, b, c));

    @Test
    void exactMultipleSelectEarnsFullPoints() {
        assertThat(ObjectiveScorer.score(multiple, answer(a, b))).isEqualByComparingTo("2.50");
    }

    @Test
    void extraOrMissingSelectionEarnsZero() {
        assertThat(ObjectiveScorer.score(multiple, answer(a, b, c))).isEqualByComparingTo("0");
        assertThat(ObjectiveScorer.score(multiple, answer(a))).isEqualByComparingTo("0");
    }

    @Test
    void unansweredEarnsZeroAndWrittenIsNotAutoScored() {
        assertThat(ObjectiveScorer.score(multiple, null)).isEqualByComparingTo("0");
        assertThat(ObjectiveScorer.score(multiple, answer())).isEqualByComparingTo("0");
        assertThat(ObjectiveScorer.score(question(QuestionType.WRITTEN, "5", List.of()), null)).isNull();
    }

    @Test
    void singleChoiceAndTrueFalseNeedTheOneCorrectOption() {
        OptionContent right = option("True", true);
        OptionContent wrong = option("False", false);
        QuestionContent trueFalse = question(QuestionType.TRUE_FALSE, "1", List.of(right, wrong));
        assertThat(ObjectiveScorer.score(trueFalse, answer(right))).isEqualByComparingTo("1");
        assertThat(ObjectiveScorer.score(trueFalse, answer(wrong))).isEqualByComparingTo("0");
    }

    private OptionContent option(String label, boolean correct) {
        return new OptionContent(UUID.randomUUID(), label, correct);
    }

    private QuestionContent question(QuestionType type, String points, List<OptionContent> options) {
        return new QuestionContent(questionId, type, "Prompt", new BigDecimal(points), "", "", options);
    }

    private AttemptAnswer answer(OptionContent... selected) {
        AttemptAnswer answer = new AttemptAnswer(UUID.randomUUID(), questionId);
        answer.save(List.of(selected).stream().map(OptionContent::id).toList(), null, Instant.now());
        return answer;
    }
}
