package com.saasresearcher.learning.audit.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum AuditOutcome {
    SUCCESS(0),
    DENIED(1),
    FAILURE(2);

    private final short code;

    AuditOutcome(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static AuditOutcome fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown audit outcome " + code));
    }
}
