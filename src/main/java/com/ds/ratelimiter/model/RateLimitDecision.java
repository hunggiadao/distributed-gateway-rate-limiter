package com.ds.ratelimiter.model;

/**
 * Stores the answer from the rate limiter.
 *
 * The gateway asks the rate limiter: "Can this client send one more request?"
 * This class carries the answer and the remaining token count.
 */
public class RateLimitDecision {
    private final boolean allowed;
    private final int remainingTokens;
    private final String reason;

    public RateLimitDecision(boolean allowed, int remainingTokens, String reason) {
        this.allowed = allowed;
        this.remainingTokens = remainingTokens;
        this.reason = reason;
    }

	// getter methods
    public boolean isAllowed() {return allowed;}
    public int getRemainingTokens() {return remainingTokens;}
    public String getReason() {return reason;}
}
