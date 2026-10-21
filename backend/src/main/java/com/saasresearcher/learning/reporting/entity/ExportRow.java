package com.saasresearcher.learning.reporting.entity;

import com.saasresearcher.learning.reporting.api.ReportType;
import java.time.Instant;
import java.util.UUID;

public record ExportRow(UUID id, UUID classId, UUID requestedBy, ReportType type, String status, String storageKey, Instant createdAt) {
}
