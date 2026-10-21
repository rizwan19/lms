package com.saasresearcher.learning.notifications.entity;

import com.saasresearcher.learning.notifications.api.JobRequest;
import java.util.UUID;

public record ClaimedJob(UUID id, UUID leaseToken, int attempts, JobRequest request) {
}
