package com.saasresearcher.learning.enrollment.service;

public final class EnrollmentErrors {

    static final String ENROLLMENT_INACTIVE = "ENROLLMENT_INACTIVE";
    static final String ALREADY_ENROLLED = "ALREADY_ENROLLED";
    static final String OWN_COURSE = "OWN_COURSE";
    static final String COURSE_UNAVAILABLE = "COURSE_UNAVAILABLE";
    static final String REQUEST_ALREADY_DECIDED = "REQUEST_ALREADY_DECIDED";
    static final String REQUEST_NOT_PENDING = "REQUEST_NOT_PENDING";
    static final String INVITATION_INVALID = "INVITATION_INVALID";
    static final String INVITATION_UNAVAILABLE = "INVITATION_UNAVAILABLE";
    static final String INVITATION_STALE = "INVITATION_STALE";
    static final String INVITATION_NOT_PENDING = "INVITATION_NOT_PENDING";
    static final String INVALID_STATUS_CHANGE = "INVALID_STATUS_CHANGE";

    private EnrollmentErrors() {
    }
}
