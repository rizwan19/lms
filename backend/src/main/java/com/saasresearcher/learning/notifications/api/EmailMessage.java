package com.saasresearcher.learning.notifications.api;

public record EmailMessage(String to, String subject, String body) {

    @Override
    public String toString() {
        return "EmailMessage[subject=" + subject + "]";
    }
}
