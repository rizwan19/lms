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
}
