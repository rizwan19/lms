package com.saasresearcher.learning.classrooms.api;

import java.util.UUID;

/** Read access to class facts for modules that schedule work against classes. Callers authorize separately. */
public interface ClassDirectory {

    ClassInfo require(UUID classId);

    boolean hasEnrollments(UUID classId);
}
