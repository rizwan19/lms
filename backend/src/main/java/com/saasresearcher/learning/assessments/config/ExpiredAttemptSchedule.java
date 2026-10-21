package com.saasresearcher.learning.assessments.config;

import com.saasresearcher.learning.assessments.service.ExpiredAttemptSweep;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "learning.jobs.enabled", matchIfMissing = true)
public class ExpiredAttemptSchedule {

    private final ExpiredAttemptSweep sweep;

    ExpiredAttemptSchedule(ExpiredAttemptSweep sweep) {
        this.sweep = sweep;
    }

    @Scheduled(fixedDelayString = "15s")
    void sweep() {
        sweep.sweep();
    }
}
