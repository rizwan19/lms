package com.saasresearcher.learning.media.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum AssetPurpose {
    VIDEO(0),
    DOCUMENT(1),
    BRANDING_IMAGE(2);

    private final short code;

    AssetPurpose(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static AssetPurpose fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown asset purpose " + code));
    }
}
