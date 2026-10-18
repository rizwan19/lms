package com.saasresearcher.learning.identity.api;

import com.saasresearcher.learning.common.Actor;
import java.util.List;

public interface AccountProvisioning {

    /**
     * Creates a student account in the caller's transaction. An email invitation is used when the student has an
     * email address and email is configured; otherwise a temporary password is returned for one-time delivery.
     */
    ProvisionedStudent createStudent(String username, String displayName, String email, Actor creator);

    /** Format problems with a prospective student's identity fields, using the same rules as account creation. */
    List<String> studentProblems(String username, String displayName, String email);
}
