package com.saasresearcher.learning.courses.repository;

import com.saasresearcher.learning.courses.entity.CourseVersion;
import com.saasresearcher.learning.courses.api.EditionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseVersionRepository extends JpaRepository<CourseVersion, UUID> {

    Optional<CourseVersion> findByCourseIdAndStatus(UUID courseId, EditionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from CourseVersion v where v.courseId = :course and v.status = com.saasresearcher.learning.courses.api.EditionStatus.DRAFT")
    Optional<CourseVersion> lockDraft(@Param("course") UUID courseId);

    List<CourseVersion> findByCourseIdAndStatusOrderByEditionDesc(UUID courseId, EditionStatus status);

    Optional<CourseVersion> findFirstByCourseIdAndStatusOrderByEditionDesc(UUID courseId, EditionStatus status);

    List<CourseVersion> findByCourseIdInAndStatusOrderByEditionDesc(Collection<UUID> courseIds, EditionStatus status);

    @Query("select coalesce(max(v.edition), 0) + 1 from CourseVersion v where v.courseId = :course")
    int nextEdition(@Param("course") UUID courseId);
}
