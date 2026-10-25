package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.ErrorCodes;
import com.saasresearcher.learning.identity.api.RateLimits;
import com.saasresearcher.learning.identity.config.IdentityProperties;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Fixed-window attempt counters in PostgreSQL so limits hold across restarts and instances.
 * Statements run outside caller transactions so a rejected login still records its attempt.
 */
@Component
public class AuthRateLimiter implements RateLimits {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdentityProperties properties;

    AuthRateLimiter(JdbcClient jdbc, Clock clock, IdentityProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    public void acquire(String bucket, int maxAttempts) {
        if (exceeded(bucket, maxAttempts)) {
            throw limited();
        }
        record(bucket);
    }

    static ApiException limited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCodes.RATE_LIMITED, "Too many attempts; try again later");
    }

    boolean exceeded(String bucket, int maxAttempts) {
        return jdbc.sql("select attempts from auth_rate_limits where bucket = :bucket and window_started_at > :cutoff")
                .param("bucket", bucket)
                .param("cutoff", windowCutoff())
                .query(Integer.class)
                .optional()
                .map(attempts -> attempts >= maxAttempts)
                .orElse(false);
    }

    void record(String bucket) {
        jdbc.sql("""
                insert into auth_rate_limits (bucket, attempts, window_started_at) values (:bucket, 1, :now)
                on conflict (bucket) do update set
                    attempts = case when auth_rate_limits.window_started_at <= :cutoff then 1 else auth_rate_limits.attempts + 1 end,
                    window_started_at = case when auth_rate_limits.window_started_at <= :cutoff then :now else auth_rate_limits.window_started_at end
                """)
                .param("bucket", bucket)
                .param("now", Timestamp.from(clock.instant()))
                .param("cutoff", windowCutoff())
                .update();
    }

    void clear(String bucket) {
        jdbc.sql("delete from auth_rate_limits where bucket = :bucket").param("bucket", bucket).update();
    }

    private Timestamp windowCutoff() {
        return Timestamp.from(clock.instant().minus(properties.rateLimitWindow()));
    }
}
