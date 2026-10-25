package com.saasresearcher.learning.certificates.api;

import java.time.LocalDate;

/** Minimal public verification answer: validity, workspace, course and date; the learner only when enabled. */
public record PublicCertificateStatus(String status, String workspace, String course, LocalDate completedOn, String learner) {

    public static PublicCertificateStatus revoked() {
        return new PublicCertificateStatus("REVOKED", null, null, null, null);
    }
}
