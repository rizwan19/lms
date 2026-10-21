package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.entity.AttemptRows.FinalizedReason;
import com.saasresearcher.learning.assessments.repository.AttemptRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Finalizes attempts whose deadline passed with nobody reading or writing them. The attempt table itself is the
 * durable work list, so a restart loses nothing; each attempt is finalized in its own transaction under its lock.
 */
@Component
public class ExpiredAttemptSweep {

    private final AttemptRepository attempts;
    private final AttemptFinalizer finalizer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    ExpiredAttemptSweep(AttemptRepository attempts, AttemptFinalizer finalizer, TransactionTemplate transactions, Clock clock) {
        this.attempts = attempts;
        this.finalizer = finalizer;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Returns the number of attempts finalized. */
    public int sweep() {
        Instant now = clock.instant();
        int finalized = 0;
        for (UUID id : attempts.dueExpired(now, 100)) {
            Boolean closed = transactions.execute(status -> attempts.lock(id)
                    .filter(attempt -> attempt.isExpired(now))
                    .map(attempt -> {
                        finalizer.finalize(attempt, FinalizedReason.EXPIRED, now);
                        return true;
                    })
                    .orElse(false));
            finalized += Boolean.TRUE.equals(closed) ? 1 : 0;
        }
        return finalized;
    }
}
