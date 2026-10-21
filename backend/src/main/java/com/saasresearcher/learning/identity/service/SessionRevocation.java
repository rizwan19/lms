package com.saasresearcher.learning.identity.service;

import java.util.UUID;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Deletes a user's stored sessions once the credential or status change has committed. */
@Component
public class SessionRevocation {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    SessionRevocation(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    void revokeAfterCommit(UUID userId) {
        revokeAfterCommit(userId, null);
    }

    void revokeAfterCommit(UUID userId, String keepSessionId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessions.findByPrincipalName(userId.toString()).keySet().stream()
                        .filter(sessionId -> !sessionId.equals(keepSessionId))
                        .forEach(sessions::deleteById);
            }
        });
    }
}
