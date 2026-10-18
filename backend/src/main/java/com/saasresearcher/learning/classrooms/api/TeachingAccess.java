package com.saasresearcher.learning.classrooms.api;

import com.saasresearcher.learning.common.Actor;
import java.util.UUID;

public interface TeachingAccess {

    /** Administrators and assigned class teachers pass; anyone else gets not found. */
    void requireClassTeacher(UUID classId, Actor actor);

    /** Whether a teacher teaches the student in any class (any enrollment state). */
    boolean canManageStudent(UUID studentId, Actor actor);
}
