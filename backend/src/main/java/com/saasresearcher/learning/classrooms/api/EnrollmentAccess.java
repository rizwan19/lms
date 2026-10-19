package com.saasresearcher.learning.classrooms.api;

import com.saasresearcher.learning.common.Actor;
import java.util.List;
import java.util.UUID;

public interface EnrollmentAccess {

    /**
     * The actor's own enrollment, usable for learning now: active enrollment, active class and open access window.
     * Someone else's enrollment is reported as not found; an inactive own enrollment as access denied.
     */
    EnrollmentView requireActive(UUID enrollmentId, Actor actor);

    /** As {@link #requireActive} but row-locks the enrollment for the caller's transaction. */
    EnrollmentView lockActive(UUID enrollmentId, Actor actor);

    /** The actor's own enrollment in any state, for historical reads. */
    StudentEnrollment requireOwn(UUID enrollmentId, Actor actor);

    List<StudentEnrollment> enrollmentsOf(UUID studentId);

    /** System lookup without an actor, for internal read models such as progress summaries. */
    EnrollmentView require(UUID enrollmentId);

    /**
     * System row lock on an enrollment for the caller's transaction. Everything that changes eligibility (progress,
     * grade publication, completion) takes this lock first, before any attempt lock.
     */
    EnrollmentView lock(UUID enrollmentId);

    /** Whether completion has frozen this enrollment's lesson completions. */
    boolean isLearningLocked(UUID enrollmentId);

    /** Freezes lesson completions once a completion exists; call within the transaction holding the enrollment lock. */
    void lockLearning(UUID enrollmentId);
}
