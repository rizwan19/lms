package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.AttemptFinalizedListener;
import com.saasresearcher.learning.assessments.api.AttemptStatus;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Notifies registered listeners (e.g. automatic grade publication) inside the finalizing transaction. */
@Component
public class AttemptFinalizationHooks {

    private final ObjectProvider<AttemptFinalizedListener> listeners;

    AttemptFinalizationHooks(ObjectProvider<AttemptFinalizedListener> listeners) {
        this.listeners = listeners;
    }

    void finalized(UUID attemptId, AttemptStatus status) {
        listeners.forEach(listener -> listener.attemptFinalized(attemptId, status));
    }
}
