package com.saasresearcher.learning.common;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
/** Review state of a teacher account; students have none. */
public enum TeacherStatus {
    PENDING(0),
    APPROVED(1),
    REJECTED(2);

    private final short code;

    TeacherStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static TeacherStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown teacher status " + code));
    }
}
