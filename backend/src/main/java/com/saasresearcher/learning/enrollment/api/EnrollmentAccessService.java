package com.saasresearcher.learning.enrollment.api;

import com.saasresearcher.learning.common.Actor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentAccessService {

    EnrollmentView requireActive(UUID enrollmentId, Actor actor);

    EnrollmentView lockActive(UUID enrollmentId, Actor actor);

    EnrollmentView require(UUID enrollmentId);

    EnrollmentView lock(UUID enrollmentId);

    LearnerEnrollment requireOwn(UUID enrollmentId, Actor actor);

    List<LearnerEnrollment> enrollmentsOf(UUID learnerId);

    Optional<EnrollmentView> find(UUID courseId, UUID learnerId);

    boolean isLearningLocked(UUID enrollmentId);

    void lockLearning(UUID enrollmentId);
}
