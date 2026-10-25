package com.saasresearcher.learning.enrollment.repository;

import com.saasresearcher.learning.enrollment.api.InvitationStatus;
import com.saasresearcher.learning.enrollment.entity.CourseInvitation;
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

public interface CourseInvitationRepository extends JpaRepository<CourseInvitation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CourseInvitation i where i.id = :id")
    Optional<CourseInvitation> lockById(@Param("id") UUID id);

    /** Unlocked lookup; acceptance takes the account and course locks before locking the invitation. */
    Optional<CourseInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CourseInvitation i where i.courseId = :course and i.recipientEmail = :email and i.status = :status")
    List<CourseInvitation> lockByCourseAndEmail(@Param("course") UUID courseId, @Param("email") String email, @Param("status") InvitationStatus status);

    Page<CourseInvitation> findByCourseId(UUID courseId, Pageable pageable);

    Page<CourseInvitation> findByCourseIdAndStatus(UUID courseId, InvitationStatus status, Pageable pageable);
}
