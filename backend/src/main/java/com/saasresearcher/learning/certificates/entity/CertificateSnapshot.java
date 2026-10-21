package com.saasresearcher.learning.certificates.entity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Everything printed on a certificate, frozen at issuance. {@code verificationToken} is private: it appears only in the
 * PDF's QR code and is never returned by the API.
 */
public record CertificateSnapshot(int templateRevision, String institutionName, UUID logoAssetId, String studentName, String courseTitle, int edition,
        LocalDate completedOn, boolean verificationEnabled, boolean showStudentName, String verificationToken) {

    public static final int TEMPLATE_REVISION = 1;
}
