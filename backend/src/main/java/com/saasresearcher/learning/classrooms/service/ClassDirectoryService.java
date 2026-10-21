package com.saasresearcher.learning.classrooms.service;

import com.saasresearcher.learning.classrooms.api.ClassDirectory;
import com.saasresearcher.learning.classrooms.api.ClassInfo;
import com.saasresearcher.learning.classrooms.entity.Classroom;
import com.saasresearcher.learning.classrooms.entity.Enrollment;
import com.saasresearcher.learning.classrooms.repository.ClassroomRepository;
import com.saasresearcher.learning.classrooms.repository.EnrollmentRepository;
import com.saasresearcher.learning.common.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassDirectoryService implements ClassDirectory {

    private final ClassroomRepository classes;
    private final EnrollmentRepository enrollments;

    ClassDirectoryService(ClassroomRepository classes, EnrollmentRepository enrollments) {
        this.classes = classes;
        this.enrollments = enrollments;
    }

    @Override
    @Transactional(readOnly = true)
    public ClassInfo require(UUID classId) {
        return classes.findById(classId).map(Classroom::toInfo).orElseThrow(ApiException::notFound);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasEnrollments(UUID classId) {
        return enrollments.existsByClassId(classId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> enrollmentOf(UUID classId, UUID studentId) {
        return enrollments.findByClassIdAndStudentId(classId, studentId).map(Enrollment::id);
    }
}
