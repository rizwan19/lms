package com.saasresearcher.learning.courses.api;

import java.util.Arrays;

public enum EditionStatus {
    DRAFT(0),
    PUBLISHED(1);

    private final short code;

    EditionStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static EditionStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown edition status " + code));
    }
}
