package com.saasresearcher.learning.media.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum AssetStatus {
    UPLOADING(0),
    VALIDATING(1),
    AVAILABLE(2),
    REJECTED(3);

    private final short code;

    AssetStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static AssetStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown asset status " + code));
    }
}
