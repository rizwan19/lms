package com.saasresearcher.learning.media.internal;

import com.saasresearcher.learning.media.api.AssetStore;
import java.io.IOException;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Readiness requires the configured media storage to be reachable and writable. */
@Component("storage")
class StorageHealthIndicator implements HealthIndicator {

    private final AssetStore store;

    StorageHealthIndicator(AssetStore store) {
        this.store = store;
    }

    @Override
    public Health health() {
        try {
            store.checkAvailable();
            return Health.up().build();
        } catch (IOException e) {
            return Health.down().withDetail("reason", e.getMessage()).build();
        }
    }
}
