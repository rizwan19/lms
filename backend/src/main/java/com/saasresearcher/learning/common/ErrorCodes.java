package com.saasresearcher.learning.common;

/** Error codes shared across modules. Module-specific codes live with their module. */
public final class ErrorCodes {

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String REVISION_CONFLICT = "REVISION_CONFLICT";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String REQUEST_REJECTED = "REQUEST_REJECTED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {
    }
}
