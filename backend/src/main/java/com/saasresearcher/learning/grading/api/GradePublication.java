package com.saasresearcher.learning.grading.api;

import com.saasresearcher.learning.common.Actor;
import java.util.UUID;

public interface GradePublication {

    /** Publishes the finalized grade revision; locks the enrollment, then the revision. */
    PublishedResult publish(UUID attemptId, long expectedRevision, Actor actor);
}
