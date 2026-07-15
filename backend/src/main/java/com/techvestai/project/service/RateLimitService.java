package com.techvestai.project.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter.
 *
 * <p>Tracks the timestamps of each request per user within a 60-second window.
 * Allows up to 100 requests per window; the 101st request within any 60-second
 * period is rejected and this method returns {@code false}.
 */
@Service
public class RateLimitService {

    private static final int MAX_REQUESTS_PER_WINDOW = 100;
    private static final long WINDOW_SECONDS = 60L;

    // userId (String) → deque of request timestamps within the current window
    private final ConcurrentHashMap<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();

    /**
     * Records a request attempt for the given user and returns whether it is allowed.
     *
     * @param userId unique identifier of the authenticated user (e.g. username or user ID string)
     * @return {@code true} if the request is within the allowed limit; {@code false} if rate-limited
     */
    public boolean isAllowed(String userId) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);

        Deque<Instant> timestamps = requestLog.computeIfAbsent(userId, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // Prune entries outside the sliding window
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }

    /**
     * Returns the number of seconds until the oldest entry in the window expires,
     * which is the recommended {@code Retry-After} value when a request is rejected.
     *
     * @param userId unique identifier of the authenticated user
     * @return seconds to wait, or 0 if no window data exists
     */
    public long retryAfterSeconds(String userId) {
        Deque<Instant> timestamps = requestLog.get(userId);
        if (timestamps == null) {
            return 0L;
        }
        synchronized (timestamps) {
            if (timestamps.isEmpty()) {
                return 0L;
            }
            Instant oldest = timestamps.peekFirst();
            long secondsUntilExpiry = WINDOW_SECONDS - (Instant.now().getEpochSecond() - oldest.getEpochSecond());
            return Math.max(0L, secondsUntilExpiry);
        }
    }
}
