package com.saasresearcher.learning.assessments.repository;

import com.saasresearcher.learning.assessments.entity.ExamQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, UUID> {

    List<ExamQuestion> findByExamIdOrderByPosition(UUID examId);

    void deleteByExamId(UUID examId);
}
