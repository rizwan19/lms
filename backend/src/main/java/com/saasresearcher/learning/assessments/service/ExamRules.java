package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.dto.ExamDraftRequest.OptionInput;
import com.saasresearcher.learning.assessments.dto.ExamDraftRequest.QuestionInput;
import com.saasresearcher.learning.assessments.dto.ExamDraftRequest;
import com.saasresearcher.learning.assessments.dto.ExamViews.OptionContent;
import com.saasresearcher.learning.assessments.dto.ExamViews.QuestionContent;
import com.saasresearcher.learning.common.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ExamRules {

    static final int MAX_QUESTIONS = 100;
    static final int MAX_TEXT = 10_000;
    static final int MAX_OPTIONS = 10;
    static final BigDecimal MAX_POINTS = new BigDecimal("1000");
    private static final List<String> TRUE_FALSE = List.of("True", "False");

    private final Map<String, String> errors = new LinkedHashMap<>();

    private ExamRules() {
    }

    static List<QuestionContent> normalize(ExamDraftRequest input, Set<UUID> questionIds, Set<UUID> optionIds) {
        ExamRules rules = new ExamRules();
        rules.text("title", input.title(), 200, true);
        rules.text("instructions", input.instructions(), MAX_TEXT, false);
        BigDecimal pass = input.passPercent() == null ? BigDecimal.valueOf(50) : input.passPercent();
        if (pass.signum() < 0 || pass.compareTo(BigDecimal.valueOf(100)) > 0 || pass.scale() > 2) {
            rules.errors.put("passPercent", "Use a percentage from 0 to 100");
        }
        List<QuestionInput> questions = input.questions() == null ? List.of() : input.questions();
        if (questions.size() > MAX_QUESTIONS) {
            rules.errors.put("questions", "At most " + MAX_QUESTIONS + " questions");
        }
        List<QuestionContent> result = new ArrayList<>();
        for (int q = 0; q < questions.size(); q++) {
            result.add(rules.question("questions[" + q + "]", questions.get(q), questionIds, optionIds));
        }
        rules.throwIfAny();
        return result;
    }

    static void requirePublishable(String path, String title, List<QuestionContent> questions) {
        ExamRules rules = new ExamRules();
        rules.text(path + ".title", title, 200, true);
        if (questions.isEmpty()) {
            rules.errors.put(path + ".questions", "Add at least one question before publishing");
        }
        for (int q = 0; q < questions.size(); q++) {
            QuestionContent question = questions.get(q);
            long correct = question.options().stream().filter(OptionContent::correct).count();
            String problem = switch (question.type()) {
                case SINGLE_CHOICE, TRUE_FALSE -> question.options().size() >= 2 && correct == 1 ? null : "Mark exactly one correct answer";
                case MULTIPLE_SELECT -> question.options().size() >= 2 && correct >= 1 ? null : "Mark at least one correct answer";
                case WRITTEN -> null;
            };
            if (problem != null) {
                rules.errors.put(path + ".questions[" + q + "].options", problem);
            }
        }
        rules.throwIfAny();
    }

    private QuestionContent question(String path, QuestionInput input, Set<UUID> draftQuestionIds, Set<UUID> draftOptionIds) {
        if (input.type() == null) {
            errors.put(path + ".type", "Required");
        }
        text(path + ".prompt", input.prompt(), MAX_TEXT, true);
        text(path + ".explanation", input.explanation(), MAX_TEXT, false);
        text(path + ".gradingNotes", input.gradingNotes(), MAX_TEXT, false);
        BigDecimal points = input.points();
        if (points == null || points.signum() <= 0 || points.compareTo(MAX_POINTS) > 0 || points.stripTrailingZeros().scale() > 2) {
            errors.put(path + ".points", "Use positive points up to 1000 with at most two decimals");
            points = BigDecimal.ONE;
        }
        List<OptionContent> options = options(path, input, draftOptionIds);
        return new QuestionContent(keep(input.id(), draftQuestionIds), input.type() == null ? QuestionType.WRITTEN : input.type(),
                trim(input.prompt()), points.setScale(2), trim(input.explanation()), trim(input.gradingNotes()), options);
    }

    private List<OptionContent> options(String path, QuestionInput input, Set<UUID> draftOptionIds) {
        List<OptionInput> options = input.options() == null ? List.of() : input.options();
        if (input.type() == QuestionType.WRITTEN) {
            return List.of();
        }
        if (input.type() == QuestionType.TRUE_FALSE) {
            boolean trueCorrect = !options.isEmpty() && options.getFirst().correct();
            boolean falseCorrect = options.size() > 1 && options.get(1).correct();
            List<OptionContent> normalized = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                UUID id = i < options.size() ? options.get(i).id() : null;
                normalized.add(new OptionContent(keep(id, draftOptionIds), TRUE_FALSE.get(i), i == 0 ? trueCorrect : falseCorrect));
            }
            return normalized;
        }
        if (options.size() > MAX_OPTIONS) {
            errors.put(path + ".options", "At most " + MAX_OPTIONS + " options");
        }
        List<OptionContent> normalized = new ArrayList<>();
        for (int o = 0; o < options.size(); o++) {
            text(path + ".options[" + o + "].label", options.get(o).label(), 1000, true);
            normalized.add(new OptionContent(keep(options.get(o).id(), draftOptionIds), trim(options.get(o).label()), options.get(o).correct()));
        }
        return normalized;
    }

    private void text(String path, String value, int max, boolean required) {
        String trimmed = trim(value);
        if (required && trimmed.isEmpty()) {
            errors.put(path, "Required");
        } else if (trimmed.length() > max) {
            errors.put(path, "At most " + max + " characters");
        }
    }

    private void throwIfAny() {
        if (!errors.isEmpty()) {
            throw ApiException.invalid(errors);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static UUID keep(UUID id, Set<UUID> existing) {
        return id != null && existing.contains(id) ? id : UUID.randomUUID();
    }
}
