package com.saasresearcher.learning.enrollment.repository;

import com.saasresearcher.learning.enrollment.api.AccessRequestStatus;
import com.saasresearcher.learning.enrollment.entity.AccessRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccessRequest r where r.courseId = :course and r.learnerId = :learner")
    Optional<AccessRequest> lockByCourseAndLearner(@Param("course") UUID courseId, @Param("learner") UUID learnerId);

    List<AccessRequest> findByLearnerIdOrderByRequestedAtDesc(UUID learnerId);

    Page<AccessRequest> findByCourseIdAndStatus(UUID courseId, AccessRequestStatus status, Pageable pageable);
}
