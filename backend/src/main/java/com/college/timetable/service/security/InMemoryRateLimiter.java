package com.college.timetable.service.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Small in-process sliding window rate limiter.
 *
 * <p>It protects the login endpoint and student lookup endpoints from trivial brute force and
 * accidental hammering on campus Wi-Fi. It is deliberately not a substitute for infrastructure
 * level rate limiting: with more than one application instance the limit becomes per instance.
 */
public class InMemoryRateLimiter {

    private final ConcurrentMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public record Decision(boolean allowed, int remaining, long retryAfterSeconds) {
    }

    /**
     * Registers a hit for {@code key}.
     *
     * @param limit      maximum hits allowed inside the window
     * @param windowSize window length
     */
    public Decision check(String key, int limit, java.time.Duration windowSize) {
        long now = clock.millis();
        long cutoff = now - windowSize.toMillis();
        Deque<Long> timestamps = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                long oldest = timestamps.peekFirst();
                long retryAfter = Math.max(1, (oldest + windowSize.toMillis() - now + 999) / 1000);
                return new Decision(false, 0, retryAfter);
            }
            timestamps.addLast(now);
            // Opportunistic cleanup so the key set cannot grow without bound.
            if (hits.size() > 10_000) {
                hits.entrySet().removeIf(entry -> {
                    synchronized (entry.getValue()) {
                        return entry.getValue().isEmpty()
                                || entry.getValue().peekLast() < now - windowSize.toMillis();
                    }
                });
            }
            return new Decision(true, limit - timestamps.size(), 0);
        }
    }

    /** Forgets the history for a key, called after a successful login. */
    public void reset(String key) {
        hits.remove(key);
    }
}