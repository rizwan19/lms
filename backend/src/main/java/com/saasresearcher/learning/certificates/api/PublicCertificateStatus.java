package com.saasresearcher.learning.certificates.api;

import java.time.LocalDate;

/** Minimal public verification answer: validity, institution, course and date; the student only when enabled. */
public record PublicCertificateStatus(String status, String institution, String course, LocalDate completedOn, String student) {

    public static PublicCertificateStatus revoked() {
        return new PublicCertificateStatus("REVOKED", null, null, null, null);
    }
}
