package com.saasresearcher.learning.learning.repository;

import com.saasresearcher.learning.learning.entity.LessonProgress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressRepository extends JpaRepository<LessonProgress, UUID> {

    List<LessonProgress> findByEnrollmentId(UUID enrollmentId);

    Optional<LessonProgress> findByEnrollmentIdAndLessonId(UUID enrollmentId, UUID lessonId);
}
