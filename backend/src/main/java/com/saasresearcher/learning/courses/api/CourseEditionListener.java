package com.saasresearcher.learning.courses.api;

import java.util.UUID;

/**
 * Hook for modules that attach content to course editions (exams). Owned by courses and implemented by the higher
 * module, so courses never depends on it. Both callbacks run inside the publishing or draft-starting transaction.
 */
public interface CourseEditionListener {

    /** Validates what is attached to the draft; throw an {@code ApiException} to stop the publication. */
    void beforePublish(UUID courseId, UUID draftVersionId);

    /** Copies what was attached to the source edition onto a new correction draft. */
    void draftStarted(UUID courseId, UUID fromVersionId, UUID draftVersionId);
}
