package com.saasresearcher.learning.enrollment.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum InvitationStatus {
    PENDING(0),
    ACCEPTED(1),
    REVOKED(2),
    EXPIRED(3);

    private final short code;

    InvitationStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static InvitationStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown invitation status " + code));
    }
}
