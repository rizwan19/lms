package com.saasresearcher.learning.courses.api;

import java.util.Arrays;

public enum CourseStatus {
    ACTIVE(0),
    ARCHIVED(1);

    private final short code;

    CourseStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static CourseStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown course status " + code));
    }
}
