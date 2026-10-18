package com.saasresearcher.learning.notifications.api;

import java.util.UUID;

public interface DurableJobs {

    /** Inserts the job in the caller's (mandatory) transaction; rollback removes it. Returns the job ID. */
    UUID enqueue(JobRequest request);
}
