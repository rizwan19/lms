package com.saasresearcher.learning.certificates.repository;

import com.saasresearcher.learning.certificates.entity.Certificate;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Certificate c where c.id = :id")
    Optional<Certificate> lockById(@Param("id") UUID id);

    Optional<Certificate> findByCompletionId(UUID completionId);

    Optional<Certificate> findByVerificationHash(String verificationHash);

    List<Certificate> findByLearnerIdOrderByCreatedAtDesc(UUID learnerId);

    List<Certificate> findByCourseIdOrderByCreatedAtDesc(UUID courseId);
}
