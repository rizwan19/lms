package com.saasresearcher.learning.notifications.api;

import java.util.UUID;

/** In-app notice; the deduplication key makes repeated delivery attempts produce one visible notice. */
public record NotificationRequest(UUID recipientId, String type, String title, String body, String link, String deduplicationKey) {
}
