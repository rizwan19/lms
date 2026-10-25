package com.saasresearcher.learning.enrollment.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum AccessRequestStatus {
    PENDING(0),
    APPROVED(1),
    REJECTED(2),
    CANCELLED(3);

    private final short code;

    AccessRequestStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static AccessRequestStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown access request status " + code));
    }
}
