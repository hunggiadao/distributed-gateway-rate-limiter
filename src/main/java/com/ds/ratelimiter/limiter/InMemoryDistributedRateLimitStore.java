package com.ds.ratelimiter.limiter;

import com.ds.ratelimiter.model.RateLimitDecision;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InMemoryDistributedRateLimitStore is an in-memory implementation of the RateLimitStore interface.
 * It manages rate-limiting state for clients using various algorithms (Token Bucket, Fixed Window, Sliding Window).
 * This implementation is thread-safe and supports global server load shedding.
 */
public class InMemoryDistributedRateLimitStore implements RateLimitStore {
    private final Map<String, RateLimitState> buckets = new ConcurrentHashMap<>();
    
	// Variables to track server-wide load shedding
    private long lastCurrentTimeMs = 0;
    private int totalRequestsThisMillisecond = 0;
    private int paymentRequestsThisMillisecond = 0;

	/**
     * Consumes a token for the given client and path based on the provided rate-limiting configuration.
     * This method enforces global server load shedding and evaluates all enabled rate-limiting algorithms.
     *
     * @param key The client ID and path (e.g., "clientId|path").
     * @param config The rate-limiting configuration.
     * @return A RateLimitDecision indicating whether the request is allowed and the remaining tokens.
     */
    @Override
    public synchronized RateLimitDecision consume(String key, RateLimitConfig config) {
        String path = key.contains("|") ? key.split("\\|")[1] : "/users";
        String clientId = key.contains("|") ? key.split("\\|")[0] : key;
        long now = System.currentTimeMillis();

        if (now != lastCurrentTimeMs) {
            lastCurrentTimeMs = now;
            totalRequestsThisMillisecond = 0;
            paymentRequestsThisMillisecond = 0;
        }

        boolean isPayment = path.startsWith("/payments");

        // --- GLOBAL SERVER LOAD SHEDDER ---
        // if (config.isLoadShedderEnabled()) {
        //     if (totalRequestsThisMillisecond >= config.getServerMaxRatePerMs()) {
        //         // The server is technically fully loaded! 
                
        //         if (isPayment) {
        //             // Calculate what percentage of the current CPU load is dedicated to payments
        //             double currentPaymentRatio = totalRequestsThisMillisecond == 0 ? 0 
        //                     : ((double) paymentRequestsThisMillisecond / totalRequestsThisMillisecond) * 100.0;
                    
        //             // If payments are being starved (below their minimum threshold), force the request through
        //             if (currentPaymentRatio < config.getPaymentsMinPercentage()) {
        //                 paymentRequestsThisMillisecond++;
        //                 totalRequestsThisMillisecond++;
        //             } else {
        //                 // Payments have met their quota, drop to protect the server
        //                 return new RateLimitDecision(false, 0, "Server overloaded. Priority quota met.");
        //             }
        //         } else {
        //             // Non-priority requests are immediately dropped when the server is full
        //             return new RateLimitDecision(false, 0, "Server overloaded.");
        //         }
        //     } else {
        //         // Server is under the limit, so ALL path types freely share the capacity
        //         totalRequestsThisMillisecond++;
        //         if (isPayment) {
        //             paymentRequestsThisMillisecond++;
        //         }
        //     }
        // }
		// --- GLOBAL SERVER LOAD SHEDDER (PREEMPTIVE APPROACH) ---
        if (config.isLoadShedderEnabled()) {
            int maxRate = config.getServerMaxRatePerMs();
            
            // 1. Check absolute server threshold (Applies to ALL traffic)
            if (totalRequestsThisMillisecond >= maxRate) {
                return new RateLimitDecision(false, 0, "Server overloaded. Max rate reached.");
            }
            
            // 2. Preemptive shedding for non-priority traffic
            if (!isPayment) {
                // Calculate the max threshold for non-payment requests
                double minPayRatio = config.getPaymentsMinPercentage() / 100.0;
                int maxNonPayment = (int) (maxRate * (1.0 - minPayRatio));
                int currentNonPayment = totalRequestsThisMillisecond - paymentRequestsThisMillisecond;
                
                // If non-payments have hit their ceiling, drop them so the remaining slots are saved for payments
                if (currentNonPayment >= maxNonPayment) {
                    return new RateLimitDecision(false, 0, "Server preemptively shedding non-payment traffic.");
                }
            }
        }

        RateLimitState fwBucket = null;
        RateLimitState swBucket = null;
        RateLimitState tbBucket = null;
        
        double minTokens = Double.MAX_VALUE;
        boolean anyEnabled = false;

        // 1. Process and evaluate Fixed Window if toggled
        if (config.isFixedWindowEnabled()) {
            anyEnabled = true;
            fwBucket = buckets.computeIfAbsent(clientId + ":fixed", k -> new RateLimitState(config.getFwLimit()));
            
            long elapsed = Math.max(0, now - fwBucket.getLastRefillTimeMillis());
            
            if (elapsed > config.getFwWindowLengthMs()) {
                // NEW: Calculate exactly how many full windows have passed
                long windowsPassed = elapsed / config.getFwWindowLengthMs();
                
                fwBucket.setTokens(config.getFwLimit());
                // CRITICAL FIX: Fast-forward the timestamp by ALL passed windows at once
                fwBucket.setLastRefillTimeMillis(fwBucket.getLastRefillTimeMillis() + (windowsPassed * config.getFwWindowLengthMs()));
            }
            
            if (fwBucket.getTokens() < minTokens) {
                minTokens = fwBucket.getTokens();
            }
        }

        // 2. Process and evaluate Sliding Window if toggled
        if (config.isSlidingWindowEnabled()) {
            anyEnabled = true;
            swBucket = buckets.computeIfAbsent(clientId + ":sliding", k -> new RateLimitState(config.getSwLimit()));
            
            long windowStart = now - config.getSwWindowLengthMs();
            Queue<Long> log = swBucket.getWindowLog();
            
            // Prune timestamps that have fallen out of the sliding time window
            while (!log.isEmpty() && log.peek() <= windowStart) {
                log.poll();
            }
            
            // Available tokens is strictly the limit minus the number of requests currently in the window
            int availableTokens = config.getSwLimit() - log.size();
            swBucket.setTokens(availableTokens); // Sync for deduction phase
            
            if (availableTokens < minTokens) {
                minTokens = availableTokens;
            }
        }

        // 3. Process and evaluate Token Bucket if toggled
        if (config.isTokenBucketEnabled()) {
            anyEnabled = true;
            tbBucket = buckets.computeIfAbsent(clientId + ":token", k -> new RateLimitState(config.getTbCapacity()));
            
            // Added Math.max to prevent negative elapsed time
            long elapsed = Math.max(0, now - tbBucket.getLastRefillTimeMillis());
            
            double restored = (elapsed / 1000.0) * config.getTbRefillRate();
            double currentTokens = Math.min(config.getTbCapacity(), tbBucket.getTokens() + restored);
            tbBucket.setTokens(currentTokens);
            tbBucket.setLastRefillTimeMillis(now);
            
            if (tbBucket.getTokens() < minTokens) {
                minTokens = tbBucket.getTokens();
            }
        }

        // Fallback if no algorithms are enabled
        if (!anyEnabled) {
            totalRequestsThisMillisecond++;
            if (isPayment) paymentRequestsThisMillisecond++;
            return new RateLimitDecision(true, Integer.MAX_VALUE, "Allowed (No algorithms active)");
        }

        // Reject if the lowest token count of toggled algorithms is exhausted
        if (minTokens < 1.0) {
            return new RateLimitDecision(false, (int) Math.floor(Math.max(0, minTokens)), "Exceeded active algorithm boundary rules");
        }

        // 4. Decrement ALL toggled algorithm balances universally by 1
        int finalRemaining = Integer.MAX_VALUE;
        if (config.isFixedWindowEnabled() && fwBucket != null) {
            fwBucket.setTokens(fwBucket.getTokens() - 1.0);
            finalRemaining = Math.min(finalRemaining, (int) fwBucket.getTokens());
        }
        if (config.isSlidingWindowEnabled() && swBucket != null) {
            // ADD the current request timestamp to the Sliding Window Log
            swBucket.getWindowLog().add(now);
            
            swBucket.setTokens(swBucket.getTokens() - 1.0);
            finalRemaining = Math.min(finalRemaining, (int) Math.floor(swBucket.getTokens()));
        }
        if (config.isTokenBucketEnabled() && tbBucket != null) {
            tbBucket.setTokens(tbBucket.getTokens() - 1.0);
            finalRemaining = Math.min(finalRemaining, (int) Math.floor(tbBucket.getTokens()));
        }

        totalRequestsThisMillisecond++;
        if (isPayment) paymentRequestsThisMillisecond++;

        return new RateLimitDecision(true, finalRemaining, "Success");
    }

	/**
     * Retrieves the minimum number of remaining tokens across all enabled algorithms.
     *
     * @param key The client ID and path.
     * @param config The rate-limiting configuration.
     * @return The minimum number of remaining tokens.
     */
    @Override
    public synchronized int getRemainingTokens(String key, RateLimitConfig config) {
        // String path = key.contains("|") ? key.split("\\|")[1] : "/users";
        String clientId = key.contains("|") ? key.split("\\|")[0] : key;
        long now = System.currentTimeMillis();
        int minTokens = Integer.MAX_VALUE;
        boolean anyEnabled = false;

        if (config.isTokenBucketEnabled()) {
            anyEnabled = true;
            RateLimitState tb = buckets.get(clientId + ":token");
            if (tb != null) {
                long elapsed = now - tb.getLastRefillTimeMillis();
                double refill = (elapsed / 1000.0) * config.getTbRefillRate();
                int val = (int) Math.floor(Math.min(config.getTbCapacity(), tb.getTokens() + refill));
                if (val < minTokens) minTokens = val;
            } else {
                if (config.getTbCapacity() < minTokens) minTokens = config.getTbCapacity();
            }
        }
        if (config.isFixedWindowEnabled()) {
            anyEnabled = true;
            RateLimitState fw = buckets.get(clientId + ":fixed");
            if (fw != null) {
                long elapsed = now - fw.getLastRefillTimeMillis();
                int val = (elapsed > config.getFwWindowLengthMs()) ? config.getFwLimit() : (int) Math.floor(fw.getTokens());
                if (val < minTokens) minTokens = val;
            } else {
                if (config.getFwLimit() < minTokens) minTokens = config.getFwLimit();
            }
        }
        if (config.isSlidingWindowEnabled()) {
            anyEnabled = true;
            RateLimitState sw = buckets.get(clientId + ":sliding");
            if (sw != null) {
                long windowStart = now - config.getSwWindowLengthMs();
                Queue<Long> log = sw.getWindowLog();
                
                // Prune old logs just for an accurate count in the UI
                while (!log.isEmpty() && log.peek() <= windowStart) {
                    log.poll();
                }
                
                int val = config.getSwLimit() - log.size();
                if (val < minTokens) minTokens = val;
            } else {
                if (config.getSwLimit() < minTokens) minTokens = config.getSwLimit();
            }
        }
		
		if (!anyEnabled) return 0;
		return minTokens == Integer.MAX_VALUE ? 0 : minTokens;
    }

	/**
     * Retrieves detailed information about the remaining tokens for each enabled algorithm.
     *
     * @param key The client ID and path.
     * @param config The rate-limiting configuration.
     * @return A string describing the remaining tokens for each algorithm.
     */
	@Override
    public synchronized String getRemainingTokensDetailed(String key, RateLimitConfig config) {
        String clientId = key.contains("|") ? key.split("\\|")[0] : key;
        long now = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();

        if (config.isTokenBucketEnabled()) {
            RateLimitState tb = buckets.get(clientId + ":token");
            int tbVal;
            if (tb != null) {
                long elapsed = Math.max(0, now - tb.getLastRefillTimeMillis());
                double tokensToAdd = (elapsed / 1000.0) * config.getTbRefillRate();
                double currentTokens = Math.min(config.getTbCapacity(), tb.getTokens() + tokensToAdd);
                tbVal = (int) Math.floor(currentTokens);
            } else {
                tbVal = config.getTbCapacity();
            }
            sb.append("TB: ").append(tbVal).append(" | ");
        }

        if (config.isFixedWindowEnabled()) {
            RateLimitState fw = buckets.get(clientId + ":fixed");
            int fwVal;
            if (fw != null) {
                long elapsed = Math.max(0, now - fw.getLastRefillTimeMillis());
                fwVal = (elapsed > config.getFwWindowLengthMs()) ? config.getFwLimit() : (int) Math.floor(fw.getTokens());
            } else {
                fwVal = config.getFwLimit();
            }
            sb.append("FW: ").append(fwVal).append(" | ");
        }

        if (config.isSlidingWindowEnabled()) {
            RateLimitState sw = buckets.get(clientId + ":sliding");
            int swVal;
            if (sw != null) {
                long windowStart = now - config.getSwWindowLengthMs();
                Queue<Long> log = sw.getWindowLog();
                int validCount = 0;
                for (Long t : log) {
                    if (t > windowStart) validCount++;
                }
                swVal = config.getSwLimit() - validCount;
            } else {
                swVal = config.getSwLimit();
            }
            sb.append("SW: ").append(swVal).append(" | ");
        }

        String res = sb.toString().trim();
        if (res.endsWith("|")) {
            res = res.substring(0, res.length() - 1).trim();
        }
        return res.isEmpty() ? "No algorithms active" : res;
    }

	/**
     * Resets all rate-limiting state, clearing all buckets and counters.
     */
    @Override
    public synchronized void reset() {
        buckets.clear();
        lastCurrentTimeMs = 0;
        totalRequestsThisMillisecond = 0;
        paymentRequestsThisMillisecond = 0;
    }
}