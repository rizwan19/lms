package com.saasresearcher.learning.grading.repository;

import com.saasresearcher.learning.grading.entity.AnswerGrade;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerGradeRepository extends JpaRepository<AnswerGrade, UUID> {

    List<AnswerGrade> findByRevisionId(UUID revisionId);

    Optional<AnswerGrade> findByRevisionIdAndQuestionId(UUID revisionId, UUID questionId);
}
