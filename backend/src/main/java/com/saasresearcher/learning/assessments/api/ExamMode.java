package com.saasresearcher.learning.assessments.api;

import java.util.Arrays;

public enum ExamMode {
    SELF_PACED(0),
    SCHEDULED(1);

    private final short code;

    ExamMode(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static ExamMode fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown exam mode " + code));
    }
}
