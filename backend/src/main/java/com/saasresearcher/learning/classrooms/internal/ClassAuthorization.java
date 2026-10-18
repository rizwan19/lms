package com.saasresearcher.learning.classrooms.internal;

import com.saasresearcher.learning.classrooms.api.TeachingAccess;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.identity.api.StudentScope;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class scope: administrators and assigned class teachers. Course authorship alone grants nothing here.
 * Also tells identity which students a teacher may manage (list, assisted reset).
 */
@Component
class ClassAuthorization implements TeachingAccess, StudentScope {

    private final ClassroomRepository classes;
    private final EnrollmentRepository enrollments;

    ClassAuthorization(ClassroomRepository classes, EnrollmentRepository enrollments) {
        this.classes = classes;
        this.enrollments = enrollments;
    }

    void requireStaff(Actor actor) {
        if (!actor.isAdministrator() && !actor.has(Role.TEACHER)) {
            throw ApiException.accessDenied("Teacher or administrator role required");
        }
    }

    Classroom requireClass(UUID classId, Actor actor) {
        requireStaff(actor);
        Classroom classroom = classes.findById(classId).orElseThrow(ApiException::notFound);
        if (!actor.isAdministrator() && !classroom.hasTeacher(actor.userId())) {
            throw ApiException.notFound();
        }
        return classroom;
    }

    Classroom lockClass(UUID classId, Actor actor) {
        requireClass(classId, actor);
        return classes.lockById(classId).orElseThrow(ApiException::notFound);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireClassTeacher(UUID classId, Actor actor) {
        requireClass(classId, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canManageStudent(UUID studentId, Actor actor) {
        if (actor.isAdministrator()) {
            return true;
        }
        List<UUID> taught = classes.classIdsTaughtBy(actor.userId());
        return !taught.isEmpty() && enrollments.studentInClasses(studentId, taught);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> managedStudentIds(UUID teacherId) {
        List<UUID> taught = classes.classIdsTaughtBy(teacherId);
        return taught.isEmpty() ? Set.of() : Set.copyOf(enrollments.studentIdsInClasses(taught));
    }
}
