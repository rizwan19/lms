package com.saasresearcher.learning.operations.api;

public interface MaintenanceMode {

    /** True while a backup or restore holds the installation read-only. */
    boolean isActive();
}
