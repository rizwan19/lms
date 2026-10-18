package com.saasresearcher.learning.media.internal;

import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "learning.jobs.enabled", matchIfMissing = true)
class MediaMaintenanceSchedule {

    private final MediaMaintenance maintenance;

    MediaMaintenanceSchedule(MediaMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Scheduled(cron = "0 17 * * * *")
    void hourly() throws IOException {
        maintenance.sweep();
    }
}
