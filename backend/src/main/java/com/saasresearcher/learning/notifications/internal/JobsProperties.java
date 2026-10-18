package com.saasresearcher.learning.notifications.internal;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("learning.jobs")
record JobsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("10") int batchSize,
        @DefaultValue("60s") Duration lease,
        @DefaultValue("20s") Duration leaseRenewal,
        @DefaultValue({"1m", "5m", "30m", "2h"}) List<Duration> retryDelays) {

    /** The execution after the last retry delay is terminal: four delays mean five attempts. */
    int maxAttempts() {
        return retryDelays.size() + 1;
    }
}
