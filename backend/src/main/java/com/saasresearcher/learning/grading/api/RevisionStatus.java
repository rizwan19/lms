package com.saasresearcher.learning.grading.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum RevisionStatus {
    DRAFT(0),
    FINALIZED(1),
    PUBLISHED(2),
    SUPERSEDED(3);

    private final short code;

    RevisionStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static RevisionStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown revision status " + code));
    }
}
