package com.saasresearcher.learning.assessments.api;

import com.saasresearcher.learning.common.Actor;
import java.util.UUID;

public interface AttemptService {

    AttemptView startAttempt(UUID examId, Actor actor);

    AttemptView get(UUID attemptId, Actor actor);
}
