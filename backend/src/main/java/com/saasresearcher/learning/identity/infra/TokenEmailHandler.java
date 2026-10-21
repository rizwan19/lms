package com.saasresearcher.learning.identity.infra;

import com.saasresearcher.learning.identity.entity.OneTimeToken;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.OneTimeTokenRepository;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.identity.service.SecureTokens;
import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.notifications.api.EmailSender;
import com.saasresearcher.learning.notifications.api.JobHandler;
import com.saasresearcher.learning.notifications.api.JobRequest;
import java.time.Clock;
import java.util.Optional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends a one-time link. Each execution rotates the token first, so after a retry only the newest email's
 * link works; a crash after SMTP acceptance may send a duplicate (documented at-least-once behaviour).
 */
public abstract class TokenEmailHandler<T extends OneTimeToken> implements JobHandler {

    private final OneTimeTokenRepository<T> tokens;
    private final UserAccountRepository accounts;
    private final EmailSender email;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String publicOrigin;

    TokenEmailHandler(OneTimeTokenRepository<T> tokens, UserAccountRepository accounts, EmailSender email,
            TransactionTemplate transactions, Clock clock, String publicOrigin) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.email = email;
        this.transactions = transactions;
        this.clock = clock;
        this.publicOrigin = publicOrigin;
    }

    @Override
    public void handle(JobRequest job) {
        transactions.execute(status -> rotate(job))
                .ifPresent(email::send);
    }

    private Optional<EmailMessage> rotate(JobRequest job) {
        Optional<T> stored = tokens.findById(job.aggregateId()).filter(token -> token.isUsable(clock.instant()));
        Optional<UserAccount> account = stored.flatMap(token -> accounts.findById(token.userId()))
                .filter(UserAccount::isActive)
                .filter(found -> found.email() != null);
        if (stored.isEmpty() || account.isEmpty()) {
            return Optional.empty();
        }
        String token = SecureTokens.newToken();
        stored.get().rotate(SecureTokens.sha256(token));
        return Optional.of(message(account.get(), publicOrigin + path() + "?token=" + token));
    }

    abstract String path();

    abstract EmailMessage message(UserAccount account, String link);
}
