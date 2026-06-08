package com.ds.ratelimiter.limiter;

import java.util.LinkedList;
import java.util.Queue;

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