package com.saasresearcher.learning.support;

import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.notifications.api.EmailSender;
import com.saasresearcher.learning.notifications.api.JobHandler;
import com.saasresearcher.learning.notifications.api.JobRequest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test doubles for outbound email and a controllable job handler. Never used outside tests. */
@TestConfiguration(proxyBeanMethods = false)
public class TestJobsConfiguration {

    @Bean
    @Primary
    CapturingEmailSender capturingEmailSender() {
        return new CapturingEmailSender();
    }

    @Bean
    RecordingJobHandler recordingJobHandler() {
        return new RecordingJobHandler();
    }

    public static class CapturingEmailSender implements EmailSender {

        private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
        private volatile boolean failing;
        private volatile boolean configured = true;

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public void send(EmailMessage message) {
            if (failing) {
                throw new IllegalStateException("SMTP unavailable");
            }
            sent.add(message);
        }

        public List<EmailMessage> sent() {
            return List.copyOf(sent);
        }

        public void failing(boolean failing) {
            this.failing = failing;
        }

        public void configured(boolean configured) {
            this.configured = configured;
        }

        public void reset() {
            sent.clear();
            failing = false;
            configured = true;
        }
    }

    public static class RecordingJobHandler implements JobHandler {

        public static final String TYPE = "test.recording";

        private final List<JobRequest> handled = new CopyOnWriteArrayList<>();
        private final AtomicInteger failuresRemaining = new AtomicInteger();

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public void handle(JobRequest job) {
            handled.add(job);
            if (failuresRemaining.getAndUpdate(remaining -> Math.max(0, remaining - 1)) > 0) {
                throw new IllegalStateException("Simulated failure");
            }
        }

        public List<JobRequest> handled() {
            return List.copyOf(handled);
        }

        public void failNext(int times) {
            failuresRemaining.set(times);
        }

        public void reset() {
            handled.clear();
            failuresRemaining.set(0);
        }
    }
}
