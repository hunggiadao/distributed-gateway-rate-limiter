package com.ds.ratelimiter.limiter;

import java.util.LinkedList;
import java.util.Queue;

/**
 * The RateLimitState class represents the state of a rate limiter for a specific client or path.
 * It stores the current token count, the last refill time, and a log of request timestamps for
 * algorithms like Sliding Window.
 */
public class RateLimitState {
    private double tokens;
    private long lastRefillTimeMillis;
    private final Queue<Long> windowLog; // NEW: Holds timestamps of requests

    public RateLimitState(int capacity) {
        this.tokens = capacity;
        this.lastRefillTimeMillis = System.currentTimeMillis();
        this.windowLog = new LinkedList<>(); // Initialize the log
    }

    public double getTokens() { return tokens; }
    public void setTokens(double tokens) { this.tokens = tokens; }

    public long getLastRefillTimeMillis() { return lastRefillTimeMillis; }
    public void setLastRefillTimeMillis(long lastRefillTimeMillis) { this.lastRefillTimeMillis = lastRefillTimeMillis; }

    // NEW: Getter for the log
    public Queue<Long> getWindowLog() { return windowLog; }
}