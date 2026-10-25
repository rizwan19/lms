package com.saasresearcher.learning.completion.repository;

import com.saasresearcher.learning.completion.entity.CourseCompletion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompletionRepository extends JpaRepository<CourseCompletion, UUID> {

    Optional<CourseCompletion> findByEnrollmentId(UUID enrollmentId);
}
