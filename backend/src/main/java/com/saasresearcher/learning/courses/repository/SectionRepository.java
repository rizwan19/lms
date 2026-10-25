package com.saasresearcher.learning.courses.repository;

import com.saasresearcher.learning.courses.entity.Section;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, UUID> {

    List<Section> findByCourseVersionIdOrderByPosition(UUID courseVersionId);

    void deleteByCourseVersionId(UUID courseVersionId);
}
