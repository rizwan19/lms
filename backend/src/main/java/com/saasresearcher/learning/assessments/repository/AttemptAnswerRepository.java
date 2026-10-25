package com.saasresearcher.learning.assessments.repository;

import com.saasresearcher.learning.assessments.entity.AttemptAnswer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, UUID> {

    List<AttemptAnswer> findByAttemptId(UUID attemptId);

    Optional<AttemptAnswer> findByAttemptIdAndQuestionId(UUID attemptId, UUID questionId);
}
