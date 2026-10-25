package com.saasresearcher.learning.grading.repository;

import com.saasresearcher.learning.grading.entity.PublishedQuestionGrade;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishedQuestionGradeRepository extends JpaRepository<PublishedQuestionGrade, UUID> {

    List<PublishedQuestionGrade> findByRevisionId(UUID revisionId);
}
