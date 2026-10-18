package com.saasresearcher.learning.identity.internal;

final class IdentityErrors {

    static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    static final String RATE_LIMITED = "RATE_LIMITED";
    static final String USERNAME_TAKEN = "USERNAME_TAKEN";
    static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    static final String LAST_ADMINISTRATOR = "LAST_ADMINISTRATOR";
    static final String INVALID_TOKEN = "INVALID_TOKEN";
    static final String SETUP_COMPLETED = "SETUP_COMPLETED";
    static final String SETUP_TOKEN_INVALID = "SETUP_TOKEN_INVALID";
    static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";
    static final String CSRF_REJECTED = "CSRF_REJECTED";

    private IdentityErrors() {
    }
}
