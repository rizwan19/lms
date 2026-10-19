package com.saasresearcher.learning.classrooms.internal;

import com.saasresearcher.learning.classrooms.api.EnrollmentStatus;
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

interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Enrollment e where e.id = :id")
    Optional<Enrollment> lockById(@Param("id") UUID id);

    Optional<Enrollment> findByClassIdAndStudentId(UUID classId, UUID studentId);

    Page<Enrollment> findByClassId(UUID classId, Pageable pageable);

    boolean existsByClassId(UUID classId);

    Page<Enrollment> findByClassIdAndStatus(UUID classId, EnrollmentStatus status, Pageable pageable);

    List<Enrollment> findByStudentIdOrderByEnrolledAtDesc(UUID studentId);

    @Query("select distinct e.studentId from Enrollment e where e.classId in :classIds")
    List<UUID> studentIdsInClasses(@Param("classIds") Collection<UUID> classIds);

    @Query("select count(e) > 0 from Enrollment e where e.studentId = :student and e.classId in :classIds")
    boolean studentInClasses(@Param("student") UUID studentId, @Param("classIds") Collection<UUID> classIds);

    @Query("""
            select count(e) > 0 from Enrollment e, Classroom c
            where e.classId = c.id and e.studentId = :student and e.status = com.saasresearcher.learning.classrooms.api.EnrollmentStatus.ACTIVE
              and c.courseVersionId in :versions and c.status = com.saasresearcher.learning.classrooms.api.ClassStatus.ACTIVE
              and (c.startsAt is null or c.startsAt <= :now) and (c.endsAt is null or c.endsAt > :now)
            """)
    boolean hasOpenEnrollmentForVersions(@Param("student") UUID studentId, @Param("versions") Collection<UUID> versionIds,
            @Param("now") java.time.Instant now);
}
