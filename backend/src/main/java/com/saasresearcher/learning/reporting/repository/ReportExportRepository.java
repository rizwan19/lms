package com.saasresearcher.learning.reporting.repository;

import com.saasresearcher.learning.reporting.entity.ReportExport;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportExportRepository extends JpaRepository<ReportExport, UUID> {

    List<ReportExport> findByRequestedByAndCourseIdOrderByCreatedAtDesc(UUID requestedBy, UUID courseId, Limit limit);
}
