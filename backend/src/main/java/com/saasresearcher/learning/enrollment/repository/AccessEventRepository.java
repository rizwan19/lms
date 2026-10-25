package com.saasresearcher.learning.enrollment.repository;

import com.saasresearcher.learning.enrollment.entity.AccessEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessEventRepository extends JpaRepository<AccessEvent, UUID> {
}
