package com.saasresearcher.learning.enrollment.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum GrantSource {
    REQUEST(0),
    INVITATION(1);

    private final short code;

    GrantSource(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static GrantSource fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown grant source " + code));
    }
}
