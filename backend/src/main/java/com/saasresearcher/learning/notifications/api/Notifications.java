package com.saasresearcher.learning.notifications.api;

public interface Notifications {

    /** Adds an in-app notice in the caller's transaction unless one with the same deduplication key exists. */
    void notify(NotificationRequest request);
}
