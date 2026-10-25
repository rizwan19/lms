package com.saasresearcher.learning.assessments.api;

import java.util.Arrays;

public enum AttemptStatus {
    IN_PROGRESS(0),
    SUBMITTED(1),
    AWAITING_GRADING(2),
    GRADED(3),
    EXPIRED(4);

    private final short code;

    AttemptStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static AttemptStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown attempt status " + code));
    }
}
