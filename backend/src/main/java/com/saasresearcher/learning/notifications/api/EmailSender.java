package com.saasresearcher.learning.notifications.api;

/**
 * Outbound email. SMTP is optional: when it is not configured, callers fall back to assisted account
 * setup and administrators see a warning. Delivery is at-least-once around SMTP acknowledgement.
 */
public interface EmailSender {

    boolean isConfigured();

    void send(EmailMessage message);
}
