package com.saasresearcher.learning.identity.internal;

import com.saasresearcher.learning.common.ApiException;
import java.time.Clock;
import java.util.Locale;
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
class LoginService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimiter rateLimiter;
    private final IdentityProperties properties;
    private final Clock clock;
    private final String timingDecoyHash;

    LoginService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, AuthRateLimiter rateLimiter,
            IdentityProperties properties, Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
        this.timingDecoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    UserAccount login(String identifier, String password, String clientIp) {
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
            throw new ApiException(HttpStatus.UNAUTHORIZED, IdentityErrors.INVALID_CREDENTIALS, "Invalid username or password");
        }
        rateLimiter.clear(accountBucket);
        return account.get();
    }
}
