package com.sendlix.group.mc.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiter to prevent users from making too many API calls.
 * Tracks the last API call time for each user and enforces a cooldown period.
 */
public class RateLimiter {
    
    private final ConcurrentMap<String, Long> lastApiCallTimes = new ConcurrentHashMap<>();
    private final long cooldownMillis;
    private final ScheduledExecutorService cleanupExecutor;
    
    /**
     * Creates a new RateLimiter with the specified cooldown period.
     *
     * @param cooldownSeconds The cooldown period in seconds between API calls
     */
    public RateLimiter(int cooldownSeconds) {
        this.cooldownMillis = cooldownSeconds * 1000L;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "RateLimiter-Cleanup");
            thread.setDaemon(true);
            return thread;
        });
        
        // Clean up old entries every minute to prevent memory leaks
        cleanupExecutor.scheduleAtFixedRate(this::cleanupOldEntries, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * Checks if a user can make an API call based on the rate limit.
     * This method should be called BEFORE making the actual API call.
     *
     * @param userId The unique identifier for the user (e.g., player name or UUID)
     * @return true if the user can make an API call, false if they need to wait
     */
    public boolean canMakeApiCall(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        Long lastCallTime = lastApiCallTimes.get(userId);
        
        if (lastCallTime == null) {
            return true; // First call for this user
        }
        
        return (currentTime - lastCallTime) >= cooldownMillis;
    }
    
    /**
     * Records that a user has made an API call.
     * This method should be called ONLY when the API call is actually made.
     *
     * @param userId The unique identifier for the user
     */
    public void recordApiCall(String userId) {
        if (userId != null && !userId.trim().isEmpty()) {
            lastApiCallTimes.put(userId, System.currentTimeMillis());
        }
    }
    
    /**
     * Gets the remaining cooldown time for a user in seconds.
     *
     * @param userId The unique identifier for the user
     * @return The remaining cooldown time in seconds, or 0 if no cooldown is active
     */
    public long getRemainingCooldownSeconds(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return 0;
        }
        
        Long lastCallTime = lastApiCallTimes.get(userId);
        if (lastCallTime == null) {
            return 0;
        }
        
        long timeSinceLastCall = System.currentTimeMillis() - lastCallTime;
        long remainingCooldown = cooldownMillis - timeSinceLastCall;

        return Math.max(0, remainingCooldown / 1000);
    }

    /**
     * Removes old entries to prevent memory leaks.
     * Entries older than twice the cooldown period are removed.
     */
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        long cleanupThreshold = cooldownMillis * 2; // Remove entries older than 2x cooldown

        lastApiCallTimes.entrySet().removeIf(entry ->
            (currentTime - entry.getValue()) > cleanupThreshold);
    }

    /**
     * Clears all rate limit data for a specific user.
     * Useful for testing or administrative purposes.
     *
     * @param userId The unique identifier for the user
     */
    public void clearUserCooldown(String userId) {
        if (userId != null) {
            lastApiCallTimes.remove(userId);
        }
    }

    /**
     * Gets the total number of users currently being tracked.
     *
     * @return The number of users in the rate limiter
     */
    public int getTrackedUserCount() {
        return lastApiCallTimes.size();
    }

    /**
     * Shuts down the rate limiter and cleanup resources.
     * Should be called during plugin shutdown.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        lastApiCallTimes.clear();
    }
}
