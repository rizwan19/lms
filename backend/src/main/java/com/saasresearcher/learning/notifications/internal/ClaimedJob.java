package com.saasresearcher.learning.notifications.internal;

import com.saasresearcher.learning.notifications.api.JobRequest;
import java.util.UUID;

record ClaimedJob(UUID id, UUID leaseToken, int attempts, JobRequest request) {
}
