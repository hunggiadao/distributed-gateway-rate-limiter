package com.ds.ratelimiter.limiter;

import com.ds.ratelimiter.model.RateLimitDecision;

/**
 * The RateLimitStore interface defines the contract for implementing a rate-limiting store.
 * It provides methods for consuming tokens, retrieving remaining tokens, and resetting the store.
 * Implementations of this interface can use different storage mechanisms (e.g., in-memory, distributed).
 */
public interface RateLimitStore {
    RateLimitDecision consume(String key, RateLimitConfig config);
    int getRemainingTokens(String key, RateLimitConfig config);
    String getRemainingTokensDetailed(String key, RateLimitConfig config);
    void reset();
}