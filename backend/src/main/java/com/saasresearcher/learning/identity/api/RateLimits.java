package com.saasresearcher.learning.identity.api;

/** Database-backed fixed-window counters shared by modules that throttle abuse-prone actions. */
public interface RateLimits {

    /** Counts one attempt in {@code bucket}; throws {@code 429 RATE_LIMITED} once {@code maxAttempts} is exceeded. */
    void acquire(String bucket, int maxAttempts);
}
