package com.saasresearcher.learning.completion.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum CompletionStatus {
    NOT_ELIGIBLE(0),
    ACHIEVED(1),
    INVALIDATED(2);

    private final short code;

    CompletionStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static CompletionStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown completion status " + code));
    }
}
