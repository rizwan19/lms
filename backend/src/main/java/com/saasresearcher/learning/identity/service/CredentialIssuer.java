package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.identity.api.TokenPurpose;
import com.saasresearcher.learning.identity.config.IdentityProperties;
import com.saasresearcher.learning.identity.entity.IdentityToken;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.infra.IdentityTokenEmailHandler;
import com.saasresearcher.learning.identity.repository.IdentityTokenRepository;
import com.saasresearcher.learning.notifications.api.DurableJobs;
import com.saasresearcher.learning.notifications.api.JobRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Hashes passwords and issues one-time tokens; callers own the transaction. */
@Component
public class CredentialIssuer {

    private final PasswordEncoder passwordEncoder;
    private final IdentityTokenRepository tokens;
    private final DurableJobs jobs;
    private final IdentityProperties properties;
    private final Clock clock;

    CredentialIssuer(PasswordEncoder passwordEncoder, IdentityTokenRepository tokens, DurableJobs jobs, IdentityProperties properties,
            Clock clock) {
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.jobs = jobs;
        this.properties = properties;
        this.clock = clock;
    }

    public String hash(String password) {
        return passwordEncoder.encode(password);
    }

    void setPassword(UserAccount account, String password) {
        account.setPassword(hash(password), clock.instant());
    }

    boolean passwordMatches(UserAccount account, String password) {
        return passwordEncoder.matches(password, account.getPasswordHash());
    }

    /**
     * Revokes the user's open tokens of the purpose, creates a new one and queues its email. The stored hash belongs to a
     * token nobody sees; the email job rotates it and sends the fresh token, so no usable token is ever persisted.
     */
    void issue(UserAccount account, TokenPurpose purpose) {
        Instant now = clock.instant();
        revokeOpen(account, java.util.List.of(purpose));
        Duration ttl = purpose == TokenPurpose.RESET_PASSWORD ? properties.passwordResetTtl() : properties.emailVerificationTtl();
        IdentityToken token = tokens.save(new IdentityToken(account.getId(), purpose, SecureTokens.sha256(SecureTokens.newToken()), now.plus(ttl), now));
        jobs.enqueue(JobRequest.now(IdentityTokenEmailHandler.TYPE, token.getId(), "identity-token-email:" + token.getId(), now));
    }

    void revokeOpen(UserAccount account, Collection<TokenPurpose> purposes) {
        Instant now = clock.instant();
        tokens.lockOpen(account.getId(), purposes).forEach(open -> open.revoke(now));
    }
}
