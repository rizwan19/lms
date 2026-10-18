package com.saasresearcher.learning.notifications.api;

/**
 * Executes one job type. Registered by the owning module as a bean. Execution is at-least-once:
 * handlers must tolerate repeats, and a thrown exception schedules a retry.
 */
public interface JobHandler {

    String type();

    void handle(JobRequest job) throws Exception;
}
