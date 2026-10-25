package com.saasresearcher.learning.identity.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum AccountStatus {
    ACTIVE(0),
    SUSPENDED(1);

    private final short code;

    AccountStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static AccountStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown account status " + code));
    }
}
