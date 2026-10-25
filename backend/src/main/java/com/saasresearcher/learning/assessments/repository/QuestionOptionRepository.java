package com.saasresearcher.learning.assessments.repository;

import com.saasresearcher.learning.assessments.entity.QuestionOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

    List<QuestionOption> findByQuestionIdInOrderByPosition(Collection<UUID> questionIds);

    void deleteByQuestionIdIn(Collection<UUID> questionIds);
}
