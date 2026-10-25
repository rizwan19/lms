package com.saasresearcher.learning.assessments.api;

import java.util.Arrays;

public enum ResultPolicy {
    AUTO_PUBLISH(0),
    TEACHER_PUBLISH(1);

    private final short code;

    ResultPolicy(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static ResultPolicy fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown result policy " + code));
    }
}
