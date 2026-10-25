package com.saasresearcher.learning.identity.service;

public final class IdentityErrors {

    static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    static final String USERNAME_TAKEN = "USERNAME_TAKEN";
    static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    static final String INVALID_TOKEN = "INVALID_TOKEN";
    static final String SETUP_COMPLETED = "SETUP_COMPLETED";
    static final String REGISTRATION_CLOSED = "REGISTRATION_CLOSED";
    static final String EMAIL_NOT_CONFIGURED = "EMAIL_NOT_CONFIGURED";
    static final String ALREADY_VERIFIED = "ALREADY_VERIFIED";
    static final String SELF_SUSPENSION = "SELF_SUSPENSION";
    static final String SELF_REVIEW = "SELF_REVIEW";
    static final String APPLICATION_COOLDOWN = "APPLICATION_COOLDOWN";
    static final String APPLICATION_NOT_ALLOWED = "APPLICATION_NOT_ALLOWED";
    public static final String CSRF_REJECTED = "CSRF_REJECTED";

    private IdentityErrors() {
    }
}
