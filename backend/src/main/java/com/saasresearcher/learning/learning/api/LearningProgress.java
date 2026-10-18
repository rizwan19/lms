package com.saasresearcher.learning.learning.api;

import java.util.UUID;

public interface LearningProgress {

    LessonSummary summary(UUID enrollmentId);
}
