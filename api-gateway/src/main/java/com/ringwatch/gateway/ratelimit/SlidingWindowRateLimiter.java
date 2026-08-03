package com.ringwatch.gateway.ratelimit;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public class SlidingWindowRateLimiter {

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> requestLog = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int limit, Duration window) {
        this(limit, window, System::currentTimeMillis);
    }

    SlidingWindowRateLimiter(int limit, Duration window, LongSupplier clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        long windowStart = now - windowMillis;
        ArrayDeque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
