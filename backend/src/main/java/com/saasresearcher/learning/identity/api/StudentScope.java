package com.saasresearcher.learning.identity.api;

import java.util.Set;
import java.util.UUID;

/**
 * Teaching relationships that let a teacher manage a student account (list, assisted reset).
 * Implemented by modules that own those relationships; identity never imports them.
 */
public interface StudentScope {

    Set<UUID> managedStudentIds(UUID teacherId);
}
