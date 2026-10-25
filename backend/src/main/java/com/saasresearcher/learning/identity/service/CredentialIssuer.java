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

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CredentialIssuer {

    private final PasswordEncoder passwordEncoder;
    private final IdentityTokenRepository identityTokenRepository;
    private final DurableJobs jobs;
    private final IdentityProperties properties;
    private final Clock clock;

    public String hash(String password) {
        return passwordEncoder.encode(password);
    }

    void setPassword(UserAccount account, String password) {
        account.setPassword(hash(password), clock.instant());
    }

    boolean passwordMatches(UserAccount account, String password) {
        return passwordEncoder.matches(password, account.getPasswordHash());
    }

    void issue(UserAccount account, TokenPurpose purpose) {
        Instant now = clock.instant();
        revokeOpen(account, java.util.List.of(purpose));
        Duration ttl = purpose == TokenPurpose.RESET_PASSWORD ? properties.passwordResetTtl() : properties.emailVerificationTtl();
        IdentityToken token = identityTokenRepository.save(new IdentityToken(account.getId(), purpose, SecureTokens.sha256(SecureTokens.newToken()), now.plus(ttl), now));
        jobs.enqueue(JobRequest.now(IdentityTokenEmailHandler.TYPE, token.getId(), "identity-token-email:" + token.getId(), now));
    }

    void revokeOpen(UserAccount account, Collection<TokenPurpose> purposes) {
        Instant now = clock.instant();
        identityTokenRepository.lockOpen(account.getId(), purposes).forEach(open -> open.revoke(now));
    }
}
