package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.ExamCatalog;
import com.saasresearcher.learning.assessments.api.PublishedExam;
import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.entity.ExamQuestion;
import com.saasresearcher.learning.assessments.entity.ExamRows.ExamRow;
import com.saasresearcher.learning.assessments.entity.ExamRows.VersionRow;
import com.saasresearcher.learning.assessments.repository.ExamContentRepository;
import com.saasresearcher.learning.assessments.repository.ExamRepository;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.courses.api.RequiredExamValidator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamCatalogService implements ExamCatalog, RequiredExamValidator {

    private final ExamRepository exams;
    private final ExamContentRepository content;

    ExamCatalogService(ExamRepository exams, ExamContentRepository content) {
        this.exams = exams;
        this.content = content;
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedExam requirePublished(UUID examVersionId) {
        VersionRow version = exams.findVersion(examVersionId).filter(row -> !row.isDraft()).orElseThrow(ApiException::notFound);
        ExamRow exam = exams.findExam(version.examId()).orElseThrow();
        List<ExamQuestion> questions = content.questions(version.id());
        BigDecimal max = questions.stream().map(ExamQuestion::points).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean mixed = questions.stream().anyMatch(question -> question.type() == QuestionType.WRITTEN);
        return new PublishedExam(version.id(), exam.id(), exam.courseId(), version.edition(), version.title(), questions.size(), max,
                version.passPercent(), mixed);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> problem(UUID courseId, List<UUID> examVersionIds) {
        boolean valid = examVersionIds.stream().allMatch(id -> exams.findVersion(id)
                .filter(version -> !version.isDraft())
                .flatMap(version -> exams.findExam(version.examId()))
                .filter(exam -> exam.courseId().equals(courseId))
                .isPresent());
        return valid ? Optional.empty() : Optional.of("Required exams must be published editions of this course's exams");
    }
}
