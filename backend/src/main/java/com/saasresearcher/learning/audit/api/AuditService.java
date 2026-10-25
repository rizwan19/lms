package com.saasresearcher.learning.audit.api;

public interface AuditService {
    void record(AuditEntry entry);
}
