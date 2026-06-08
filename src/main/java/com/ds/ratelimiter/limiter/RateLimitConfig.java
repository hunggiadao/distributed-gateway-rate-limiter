package com.ds.ratelimiter.limiter;

public class RateLimitConfig {
	// token bucket
    private final boolean tokenBucketEnabled;
    private final int tbCapacity;
    private final double tbRefillRate;

	// fixed window
    private final boolean fixedWindowEnabled;
    private final long fwWindowLengthMs;
    private final int fwLimit;

	// sliding window
    private final boolean slidingWindowEnabled;
    private final long swWindowLengthMs;
    private final int swLimit;

	// fleet usage load shedder
    private final boolean loadShedderEnabled;
    private final double paymentsMinPercentage; 
    private final int serverMaxRatePerMs;       

    public RateLimitConfig(boolean tokenBucketEnabled, int tbCapacity, double tbRefillRate,
                           boolean fixedWindowEnabled, long fwWindowLengthMs, int fwLimit,
                           boolean slidingWindowEnabled, long swWindowLengthMs, int swLimit,
                           boolean loadShedderEnabled, double paymentsMinPercentage, int serverMaxRatePerMs) {
        this.tokenBucketEnabled = tokenBucketEnabled;
        this.tbCapacity = tbCapacity;
        this.tbRefillRate = tbRefillRate;
        this.fixedWindowEnabled = fixedWindowEnabled;
        this.fwWindowLengthMs = fwWindowLengthMs;
        this.fwLimit = fwLimit;
        this.slidingWindowEnabled = slidingWindowEnabled;
        this.swWindowLengthMs = swWindowLengthMs;
        this.swLimit = swLimit;
        this.loadShedderEnabled = loadShedderEnabled;
        this.paymentsMinPercentage = paymentsMinPercentage;
        this.serverMaxRatePerMs = serverMaxRatePerMs;
    }

    public boolean isTokenBucketEnabled() { return tokenBucketEnabled; }
    public int getTbCapacity() { return tbCapacity; }
    public double getTbRefillRate() { return tbRefillRate; }

    public boolean isFixedWindowEnabled() { return fixedWindowEnabled; }
    public long getFwWindowLengthMs() { return fwWindowLengthMs; }
    public int getFwLimit() { return fwLimit; }

    public boolean isSlidingWindowEnabled() { return slidingWindowEnabled; }
    public long getSwWindowLengthMs() { return swWindowLengthMs; }
    public int getSwLimit() { return swLimit; }

    public boolean isLoadShedderEnabled() { return loadShedderEnabled; }
    public double getPaymentsMinPercentage() { return paymentsMinPercentage; }
    public int getServerMaxRatePerMs() { return serverMaxRatePerMs; }
}