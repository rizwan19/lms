package com.saasresearcher.learning.completion.infra;

import com.saasresearcher.learning.completion.service.CompletionReconciler;
import com.saasresearcher.learning.notifications.api.JobHandler;
import com.saasresearcher.learning.notifications.api.JobRequest;
import org.springframework.stereotype.Component;

@Component
public class CompletionJobHandler implements JobHandler {

    private final CompletionReconciler reconciler;

    CompletionJobHandler(CompletionReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Override
    public String type() {
        return CompletionReconciler.JOB_TYPE;
    }

    @Override
    public void handle(JobRequest job) {
        reconciler.reconcile(job.aggregateId());
    }
}
