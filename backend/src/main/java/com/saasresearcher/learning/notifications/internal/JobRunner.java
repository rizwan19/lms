package com.saasresearcher.learning.notifications.internal;

import com.saasresearcher.learning.notifications.api.JobHandler;
import com.saasresearcher.learning.operations.api.MaintenanceMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Claims and executes durable jobs; handlers are looked up by type and never imported. */
@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final JobStore store;
    private final MaintenanceMode maintenance;
    private final JobsProperties properties;
    private final Map<String, JobHandler> handlers;
    private final Set<ClaimedJob> running = ConcurrentHashMap.newKeySet();

    JobRunner(JobStore store, MaintenanceMode maintenance, JobsProperties properties, List<JobHandler> handlers) {
        this.store = store;
        this.maintenance = maintenance;
        this.properties = properties;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
    }

    /** Claims one batch and executes it; returns the number of jobs executed. Nothing is claimed during maintenance. */
    public int runOnce() {
        if (maintenance.isActive()) {
            return 0;
        }
        List<ClaimedJob> claimed = store.claim(properties.batchSize());
        claimed.forEach(this::execute);
        return claimed.size();
    }

    /** Executes every currently due job, including retries made due by the caller's clock. */
    public void drain() {
        while (runOnce() > 0) {
            // keep claiming until nothing is due
        }
    }

    void renewLeases() {
        running.forEach(store::renewLease);
    }

    private void execute(ClaimedJob job) {
        JobHandler handler = handlers.get(job.request().type());
        running.add(job);
        try {
            if (handler == null) {
                throw new IllegalStateException("No handler registered for job type " + job.request().type());
            }
            handler.handle(job.request());
            store.complete(job);
        } catch (Exception e) {
            log.warn("Job {} of type {} failed on attempt {}", job.id(), job.request().type(), job.attempts(), e);
            store.fail(job, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            running.remove(job);
        }
    }
}
