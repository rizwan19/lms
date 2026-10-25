package com.saasresearcher.learning.common;

public final class ErrorCodes {

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String REVISION_CONFLICT = "REVISION_CONFLICT";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String REQUEST_REJECTED = "REQUEST_REJECTED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String EMAIL_UNVERIFIED = "EMAIL_UNVERIFIED";
    public static final String TEACHER_APPROVAL_REQUIRED = "TEACHER_APPROVAL_REQUIRED";
    public static final String COURSE_AUTHOR_REQUIRED = "COURSE_AUTHOR_REQUIRED";
    public static final String APPLICATION_ALREADY_DECIDED = "APPLICATION_ALREADY_DECIDED";
    public static final String ACCESS_REQUEST_COOLDOWN = "ACCESS_REQUEST_COOLDOWN";
    public static final String INVITATION_EMAIL_MISMATCH = "INVITATION_EMAIL_MISMATCH";
    public static final String REQUIRED_EXAM_CLOSED = "REQUIRED_EXAM_CLOSED";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    private ErrorCodes() {
    }
}
