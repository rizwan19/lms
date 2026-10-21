package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.audit.api.AuditOutcome;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.identity.config.IdentityProperties;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Password sign-in with database-backed rate limits. Deliberately not transactional: failed
 * attempts must be recorded even though the request fails.
 */
@Service
public class LoginService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimiter rateLimiter;
    private final AuditLog audit;
    private final IdentityProperties properties;
    private final Clock clock;
    private final String timingDecoyHash;

    LoginService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, AuthRateLimiter rateLimiter, AuditLog audit,
            IdentityProperties properties, Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.timingDecoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public UserAccount login(String identifier, String password, String clientIp) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        String accountBucket = "login:" + normalized + "|" + clientIp;
        String ipBucket = "login-ip:" + clientIp;
        if (rateLimiter.exceeded(accountBucket, properties.loginAttemptsPerAccount())
                || rateLimiter.exceeded(ipBucket, properties.loginAttemptsPerIp())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, IdentityErrors.RATE_LIMITED, "Too many sign-in attempts; try again later");
        }

        Optional<UserAccount> account = normalized.contains("@") ? accounts.findByEmail(normalized) : accounts.findByUsername(normalized);
        String hash = account.map(UserAccount::passwordHash).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(password == null ? "" : password, hash == null ? timingDecoyHash : hash);
        if (!passwordMatches || !account.get().canSignIn(clock.instant())) {
            rateLimiter.record(accountBucket);
            rateLimiter.record(ipBucket);
            UUID accountId = account.map(UserAccount::id).orElse(null);
            audit.record(new AuditEntry(null, "login.failed", "user", accountId, AuditOutcome.FAILURE, Map.of()));
            throw new ApiException(HttpStatus.UNAUTHORIZED, IdentityErrors.INVALID_CREDENTIALS, "Invalid username or password");
        }
        rateLimiter.clear(accountBucket);
        audit.record(AuditEntry.success(account.get().id(), "login.succeeded", "user", account.get().id()));
        return account.get();
    }
}
