package com.saasresearcher.learning.reporting.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum ExportStatus {
    PENDING(0),
    READY(1),
    FAILED(2);

    private final short code;

    ExportStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static ExportStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown export status " + code));
    }
}
