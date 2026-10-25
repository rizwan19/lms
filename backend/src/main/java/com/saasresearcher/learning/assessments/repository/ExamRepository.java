package com.saasresearcher.learning.assessments.repository;

import com.saasresearcher.learning.assessments.entity.Exam;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    List<Exam> findByCourseVersionIdOrderByCreatedAtAscIdAsc(UUID courseVersionId);
}
