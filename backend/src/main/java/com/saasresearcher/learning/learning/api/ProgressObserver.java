package com.saasresearcher.learning.learning.api;

import java.util.UUID;

/**
 * Integration seam for modules that react to progress (completion reconciliation). Called inside the saving
 * transaction while the enrollment is locked, so implementations must only record durable follow-up work.
 */
public interface ProgressObserver {

    void progressChanged(UUID enrollmentId);
}
