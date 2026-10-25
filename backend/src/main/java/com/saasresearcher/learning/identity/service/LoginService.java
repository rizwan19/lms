package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditOutcome;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.identity.config.IdentityProperties;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Password sign-in with database-backed rate limits. Deliberately not transactional: failed attempts must be recorded
 * even though the request fails. Unverified accounts may sign in, but only to verify, resend or sign out.
 */
@Service
public class LoginService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimiter rateLimiter;
    private final AuditService audit;
    private final IdentityProperties properties;
    private final String timingDecoyHash;

    LoginService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, AuthRateLimiter rateLimiter, AuditService audit,
            IdentityProperties properties) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.properties = properties;
        this.timingDecoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public UserAccount login(String identifier, String password, String clientIp) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        String accountBucket = "login:" + normalized + "|" + clientIp;
        String ipBucket = "login-ip:" + clientIp;
        if (rateLimiter.exceeded(accountBucket, properties.loginAttemptsPerAccount())
                || rateLimiter.exceeded(ipBucket, properties.loginAttemptsPerIp())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, com.saasresearcher.learning.common.ErrorCodes.RATE_LIMITED,
                    "Too many sign-in attempts; try again later");
        }

        Optional<UserAccount> account = normalized.contains("@") ? accounts.findByEmail(normalized) : accounts.findByUsername(normalized);
        String hash = account.map(UserAccount::getPasswordHash).orElse(timingDecoyHash);
        boolean passwordMatches = passwordEncoder.matches(password == null ? "" : password, hash);
        if (!passwordMatches || !account.get().isActive()) {
            rateLimiter.record(accountBucket);
            rateLimiter.record(ipBucket);
            UUID accountId = account.map(UserAccount::getId).orElse(null);
            audit.record(new AuditEntry(null, "login.failed", "user", accountId, AuditOutcome.FAILURE, Map.of()));
            throw new ApiException(HttpStatus.UNAUTHORIZED, IdentityErrors.INVALID_CREDENTIALS, "Invalid username or password");
        }
        rateLimiter.clear(accountBucket);
        audit.record(AuditEntry.success(account.get().getId(), "login.succeeded", "user", account.get().getId()));
        return account.get();
    }
}
