package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.dto.ExamViews.OptionContent;
import com.saasresearcher.learning.assessments.dto.ExamViews.QuestionContent;
import com.saasresearcher.learning.assessments.entity.ExamQuestion;
import com.saasresearcher.learning.assessments.entity.QuestionOption;
import com.saasresearcher.learning.assessments.repository.ExamQuestionRepository;
import com.saasresearcher.learning.assessments.repository.QuestionOptionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExamContentStore {

    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionOptionRepository questionOptionRepository;

    List<QuestionContent> getQuestionContents(UUID examId) {
        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByPosition(examId);
        Map<UUID, List<OptionContent>> optionsByQuestion = questionOptionRepository.findByQuestionIdInOrderByPosition(questions.stream().map(ExamQuestion::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId,
                        Collectors.mapping(option -> new OptionContent(option.getId(), option.getLabel(), option.isCorrect()), Collectors.toList())));
        return questions.stream()
                .map(row -> new QuestionContent(row.getId(), row.getType(), row.getPrompt(), row.getPoints(), row.getExplanation(), row.getGradingNotes(),
                        optionsByQuestion.getOrDefault(row.getId(), List.of())))
                .toList();
    }

    Optional<QuestionContent> question(UUID examId, UUID questionId) {
        return getQuestionContents(examId).stream().filter(question -> question.id().equals(questionId)).findFirst();
    }

    Set<UUID> questionIds(UUID examId) {
        return examQuestionRepository.findByExamIdOrderByPosition(examId).stream().map(ExamQuestion::getId).collect(Collectors.toSet());
    }

    Set<UUID> optionIds(UUID examId) {
        return questionOptionRepository.findByQuestionIdInOrderByPosition(questionIds(examId)).stream().map(QuestionOption::getId).collect(Collectors.toSet());
    }

    record Summary(int questionCount, BigDecimal maxPoints, boolean mixed) {
    }

    Summary summary(UUID examId) {
        List<QuestionContent> all = getQuestionContents(examId);
        return new Summary(all.size(), all.stream().map(QuestionContent::points).reduce(BigDecimal.ZERO, BigDecimal::add),
                all.stream().anyMatch(question -> question.type() == QuestionType.WRITTEN));
    }

    void copy(UUID fromExamId, UUID toExamId) {
        replace(toExamId, getQuestionContents(fromExamId).stream()
                .map(question -> new QuestionContent(UUID.randomUUID(), question.type(), question.prompt(), question.points(), question.explanation(),
                        question.gradingNotes(), question.options().stream()
                                .map(option -> new OptionContent(UUID.randomUUID(), option.label(), option.correct())).toList()))
                .toList());
    }

    void replace(UUID examId, List<QuestionContent> content) {
        questionOptionRepository.deleteByQuestionIdIn(questionIds(examId));
        questionOptionRepository.flush();
        examQuestionRepository.deleteByExamId(examId);
        examQuestionRepository.flush();
        for (int q = 0; q < content.size(); q++) {
            QuestionContent question = content.get(q);
            examQuestionRepository.save(new ExamQuestion(question.id(), examId, q, question.type(), question.prompt(), question.points(), question.explanation(),
                    question.gradingNotes()));
            for (int o = 0; o < question.options().size(); o++) {
                OptionContent option = question.options().get(o);
                questionOptionRepository.save(new QuestionOption(option.id(), question.id(), o, option.label(), option.correct()));
            }
        }
        examQuestionRepository.flush();
        questionOptionRepository.flush();
    }
}
