package com.saasresearcher.learning.enrollment.api;

import java.util.Arrays;

public enum EnrollmentStatus {
    ACTIVE(0),
    SUSPENDED(1),
    WITHDRAWN(2);

    private final short code;

    EnrollmentStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static EnrollmentStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown enrollment status " + code));
    }
}
