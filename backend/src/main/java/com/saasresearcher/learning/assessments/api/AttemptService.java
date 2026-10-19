package com.saasresearcher.learning.assessments.api;

import com.saasresearcher.learning.common.Actor;
import java.util.UUID;

public interface AttemptService {

    /** Starts an attempt, or returns the student's attempt already in progress (repeated starts are safe). */
    AttemptView start(UUID assignmentId, Actor actor);

    /** The student's own attempt; an attempt past its deadline is finalized first. */
    AttemptView get(UUID attemptId, Actor actor);
}
