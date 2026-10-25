package com.saasresearcher.learning.courses.repository;

import com.saasresearcher.learning.courses.api.CourseStatus;
import com.saasresearcher.learning.courses.entity.Course;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    Page<Course> findByAuthorIdAndStatus(UUID authorId, CourseStatus status, Pageable pageable);

    @Query("""
            select c from Course c where c.status = com.saasresearcher.learning.courses.api.CourseStatus.ACTIVE
              and exists (select v.id from CourseVersion v where v.courseId = c.id
                  and v.status = com.saasresearcher.learning.courses.api.EditionStatus.PUBLISHED)
              and lower(c.title) like :pattern
              and (:author is null or c.authorId = :author)
            """)
    Page<Course> catalog(@Param("pattern") String pattern, @Param("author") UUID authorId, Pageable pageable);
}
