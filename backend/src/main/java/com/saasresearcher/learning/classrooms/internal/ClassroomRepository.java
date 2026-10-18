package com.saasresearcher.learning.classrooms.internal;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Classroom c where c.id = :id")
    Optional<Classroom> lockById(@Param("id") UUID id);

    @Query("select c from Classroom c where :teacher member of c.teacherIds")
    Page<Classroom> findByTeacher(@Param("teacher") UUID teacherId, Pageable pageable);

    List<Classroom> findByIdIn(Collection<UUID> ids);

    @Query("select count(c) > 0 from Classroom c where c.courseVersionId in :versions and :teacher member of c.teacherIds")
    boolean teachesAnyVersion(@Param("teacher") UUID teacherId, @Param("versions") Set<UUID> versionIds);

    @Query("select c.id from Classroom c where :teacher member of c.teacherIds")
    List<UUID> classIdsTaughtBy(@Param("teacher") UUID teacherId);
}
