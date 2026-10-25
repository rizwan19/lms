package com.saasresearcher.learning.identity.infra;

import com.saasresearcher.learning.identity.entity.IdentityToken;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.IdentityTokenRepository;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.identity.service.SecureTokens;
import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.notifications.api.EmailSender;
import com.saasresearcher.learning.notifications.api.JobHandler;
import com.saasresearcher.learning.notifications.api.JobRequest;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends a one-time identity link. Each execution rotates the token under its row lock first, so after a retry only the
 * newest email's link works and a token consumed meanwhile is never made usable again; a crash after SMTP acceptance
 * may send a duplicate (documented at-least-once behaviour). Failures leave the account unverified.
 */
@Component
public class IdentityTokenEmailHandler implements JobHandler {

    public static final String TYPE = "identity.token-email";

    private final IdentityTokenRepository tokens;
    private final UserAccountRepository accounts;
    private final EmailSender email;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String publicOrigin;

    IdentityTokenEmailHandler(IdentityTokenRepository tokens, UserAccountRepository accounts, EmailSender email, TransactionTemplate transactions,
            Clock clock, @Value("${learning.public-origin}") String publicOrigin) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.email = email;
        this.transactions = transactions;
        this.clock = clock;
        this.publicOrigin = publicOrigin;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(JobRequest job) {
        transactions.execute(status -> rotate(job)).ifPresent(email::send);
    }

    private Optional<EmailMessage> rotate(JobRequest job) {
        Optional<IdentityToken> stored = tokens.lockById(job.aggregateId()).filter(token -> token.isUsable(clock.instant()));
        Optional<UserAccount> account = stored.flatMap(token -> accounts.findById(token.getUserId())).filter(UserAccount::isActive);
        if (stored.isEmpty() || account.isEmpty()) {
            return Optional.empty();
        }
        String token = SecureTokens.newToken();
        stored.get().rotate(SecureTokens.sha256(token));
        return Optional.of(message(stored.get(), account.get(), token));
    }

    private EmailMessage message(IdentityToken token, UserAccount account, String raw) {
        String hello = "Hello " + account.getDisplayName() + ",\n\n";
        return switch (token.getPurpose()) {
            case VERIFY_EMAIL, OWNER_VERIFY_EMAIL -> new EmailMessage(account.getEmail(), "Verify your learning portal email",
                    hello + "Confirm your email address within 24 hours using this link:\n\n" + publicOrigin + "/verify-email?token=" + raw
                            + "\n\nIf you requested several emails, use the latest one.\n");
            case RESET_PASSWORD -> new EmailMessage(account.getEmail(), "Reset your learning portal password",
                    hello + "Use this link within 30 minutes to choose a new password:\n\n" + publicOrigin + "/reset-password?token=" + raw
                            + "\n\nIf you did not ask for this, you can ignore this email.\n");
        };
    }
}
