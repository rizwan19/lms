package com.saasresearcher.learning.certificates.api;

import java.util.Arrays;

// Stored as code; codes are permanent, never reuse or renumber one.
public enum CertificateStatus {
    PENDING_RENDER(0),
    ISSUED(1),
    RENDER_FAILED(2),
    REVOKED(3);

    private final short code;

    CertificateStatus(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static CertificateStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown certificate status " + code));
    }
}
