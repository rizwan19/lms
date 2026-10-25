package com.saasresearcher.learning.identity.repository;

import com.saasresearcher.learning.identity.api.ApplicationStatus;
import com.saasresearcher.learning.identity.entity.TeacherApplication;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherApplicationRepository extends JpaRepository<TeacherApplication, UUID> {


    Optional<TeacherApplication> findFirstByApplicantIdOrderByCreatedAtDesc(UUID applicantId);

    Page<TeacherApplication> findByStatus(ApplicationStatus status, Pageable pageable);
}
