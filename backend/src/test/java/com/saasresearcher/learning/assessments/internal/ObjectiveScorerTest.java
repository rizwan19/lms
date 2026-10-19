package com.saasresearcher.learning.assessments.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.internal.AttemptRows.Answer;
import com.saasresearcher.learning.assessments.internal.AttemptRows.SnapshotOption;
import com.saasresearcher.learning.assessments.internal.AttemptRows.SnapshotQuestion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectiveScorerTest {

    private final SnapshotOption a = new SnapshotOption(UUID.randomUUID(), "A", true);
    private final SnapshotOption b = new SnapshotOption(UUID.randomUUID(), "B", true);
    private final SnapshotOption c = new SnapshotOption(UUID.randomUUID(), "C", false);
    private final SnapshotQuestion multiple = question(QuestionType.MULTIPLE_SELECT, "2.50", List.of(a, b, c));

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
        SnapshotOption right = new SnapshotOption(UUID.randomUUID(), "True", true);
        SnapshotOption wrong = new SnapshotOption(UUID.randomUUID(), "False", false);
        SnapshotQuestion trueFalse = question(QuestionType.TRUE_FALSE, "1", List.of(right, wrong));
        assertThat(ObjectiveScorer.score(trueFalse, answer(right))).isEqualByComparingTo("1");
        assertThat(ObjectiveScorer.score(trueFalse, answer(wrong))).isEqualByComparingTo("0");
    }

    private static SnapshotQuestion question(QuestionType type, String points, List<SnapshotOption> options) {
        return new SnapshotQuestion(UUID.randomUUID(), 0, type, "Prompt", new BigDecimal(points), "", "", options, null);
    }

    private static Answer answer(SnapshotOption... selected) {
        return new Answer(UUID.randomUUID(), List.of(selected).stream().map(SnapshotOption::id).toList(), null, 1, Instant.now());
    }
}
