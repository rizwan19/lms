package com.saasresearcher.learning.common;

import java.util.Arrays;

public enum Role {
    STUDENT(0),
    TEACHER(1);

    private final short code;

    Role(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static Role fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role " + code));
    }
}
