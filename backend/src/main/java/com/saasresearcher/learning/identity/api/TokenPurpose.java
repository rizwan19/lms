package com.saasresearcher.learning.identity.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum TokenPurpose {
    VERIFY_EMAIL(0),
    OWNER_VERIFY_EMAIL(1),
    RESET_PASSWORD(2);

    private final short code;

    TokenPurpose(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static TokenPurpose fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown token purpose " + code));
    }
}
