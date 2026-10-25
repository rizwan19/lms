package com.saasresearcher.learning.identity.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum ApplicationStatus {
    PENDING(0),
    APPROVED(1),
    REJECTED(2);

    private final short code;

    ApplicationStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static ApplicationStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown application status " + code));
    }
}
