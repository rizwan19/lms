package com.saasresearcher.learning.reporting.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum ReportType {
    PROGRESS(0),
    SCORES(1);

    private final short code;

    ReportType(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static ReportType fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown report type " + code));
    }
}
