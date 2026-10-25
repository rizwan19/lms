package com.saasresearcher.learning.notifications.api;

import java.util.Arrays;

public enum JobStatus {
    PENDING(0),
    RUNNING(1),
    SUCCEEDED(2),
    FAILED(3);

    private final short code;

    JobStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static JobStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown job status " + code));
    }
}
