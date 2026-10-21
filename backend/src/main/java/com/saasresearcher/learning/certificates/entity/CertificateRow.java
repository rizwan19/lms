package com.saasresearcher.learning.certificates.entity;

import com.saasresearcher.learning.certificates.api.CertificateStatus;
import java.time.Instant;
import java.util.UUID;

public record CertificateRow(UUID id, UUID completionId, UUID studentId, UUID classId, CertificateStatus status, CertificateSnapshot snapshot,
        String storageKey, boolean rendered, String renderError, String revokedReason, Instant issuedAt) {
}
