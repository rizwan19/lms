package com.saasresearcher.learning.enrollment.repository;

import com.saasresearcher.learning.enrollment.api.EnrollmentStatus;
import com.saasresearcher.learning.enrollment.entity.CourseEnrollment;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, UUID> {


    Optional<CourseEnrollment> findByCourseIdAndLearnerId(UUID courseId, UUID learnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CourseEnrollment e where e.courseId = :course and e.learnerId = :learner")
    Optional<CourseEnrollment> lockByCourseAndLearner(@Param("course") UUID courseId, @Param("learner") UUID learnerId);

    List<CourseEnrollment> findByLearnerIdOrderByGrantedAtDesc(UUID learnerId);

    Page<CourseEnrollment> findByCourseId(UUID courseId, Pageable pageable);

    Page<CourseEnrollment> findByCourseIdAndStatus(UUID courseId, EnrollmentStatus status, Pageable pageable);

    boolean existsByLearnerIdAndStatusAndCourseVersionIdIn(UUID learnerId, EnrollmentStatus status, Collection<UUID> versionIds);
}
