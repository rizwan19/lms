package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.repository.ExamAttemptRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class ExpiredAttemptSweep {

    private final ExamAttemptRepository examAttemptRepository;
    private final AttemptFinalizer attemptFinalizer;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public int sweep() {
        Instant now = clock.instant();
        int finalized = 0;
        for (UUID id : examAttemptRepository.dueExpired(now, Limit.of(100))) {
            Boolean closed = transactionTemplate.execute(status -> examAttemptRepository.lockById(id)
                    .filter(attempt -> attempt.isExpired())
                    .map(attempt -> {
                        attemptFinalizer.finalize(attempt, now);
                        return true;
                    })
                    .orElse(false));
            finalized += Boolean.TRUE.equals(closed) ? 1 : 0;
        }
        return finalized;
    }
}
