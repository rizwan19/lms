package com.saasresearcher.learning.assessments.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "learning.jobs.enabled", matchIfMissing = true)
class ExpiredAttemptSchedule {

    private final ExpiredAttemptSweep sweep;

    ExpiredAttemptSchedule(ExpiredAttemptSweep sweep) {
        this.sweep = sweep;
    }

    @Scheduled(fixedDelayString = "15s")
    void sweep() {
        sweep.sweep();
    }
}
