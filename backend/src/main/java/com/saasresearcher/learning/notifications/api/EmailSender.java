package com.saasresearcher.learning.notifications.api;

public interface EmailSender {

    boolean isConfigured();

    void send(EmailMessage message);
}
