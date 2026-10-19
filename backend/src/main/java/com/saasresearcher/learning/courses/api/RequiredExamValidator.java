package com.saasresearcher.learning.courses.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks the required exam editions of a course edition. Owned by courses and implemented by the module that
 * owns exams, so courses never depends on it.
 */
public interface RequiredExamValidator {

    /** Returns a problem description when any ID is not a published exam edition of this course. */
    Optional<String> problem(UUID courseId, List<UUID> examVersionIds);
}
