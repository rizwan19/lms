package com.saasresearcher.learning.courses.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum VideoProvider {
    YOUTUBE(0),
    VIMEO(1);

    private final short code;

    VideoProvider(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static VideoProvider fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown video provider " + code));
    }
}
