package com.ds.ratelimiter.limiter;

import com.ds.ratelimiter.model.RateLimitDecision;

public interface RateLimitStore {
    RateLimitDecision consume(String key, RateLimitConfig config);
    int getRemainingTokens(String key, RateLimitConfig config);
    
    // NEW METHOD
    String getRemainingTokensDetailed(String key, RateLimitConfig config);
    
    void reset();
}