package com.saasresearcher.learning.notifications.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Polls for due jobs inside the application; disable with learning.jobs.enabled=false (tests, restore drills). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "learning.jobs.enabled", matchIfMissing = true)
class JobScheduling {

    private final JobRunner runner;

    JobScheduling(JobRunner runner) {
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${learning.jobs.poll-interval:2s}")
    void poll() {
        runner.drain();
    }

    @Scheduled(fixedRateString = "${learning.jobs.lease-renewal:20s}")
    void renewLeases() {
        runner.renewLeases();
    }
}
