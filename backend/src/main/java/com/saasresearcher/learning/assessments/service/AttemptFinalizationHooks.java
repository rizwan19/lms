package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.AttemptFinalizedListener;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AttemptFinalizationHooks {

    private final ObjectProvider<AttemptFinalizedListener> listeners;

    AttemptFinalizationHooks(ObjectProvider<AttemptFinalizedListener> listeners) {
        this.listeners = listeners;
    }

    void finalized(UUID attemptId, boolean needsAuthor) {
        listeners.forEach(listener -> listener.attemptFinalized(attemptId, needsAuthor));
    }
}
