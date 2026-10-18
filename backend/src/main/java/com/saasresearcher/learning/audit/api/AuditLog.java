package com.saasresearcher.learning.audit.api;

public interface AuditLog {

    /** Appends the entry within the caller's transaction, so it commits or rolls back with the change. */
    void record(AuditEntry entry);
}
