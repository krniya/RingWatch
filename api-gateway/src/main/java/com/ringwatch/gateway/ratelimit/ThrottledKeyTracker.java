package com.ringwatch.gateway.ratelimit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FR17: records every rate-limit rejection and maintains the current top-N most-throttled keys.
 * Owns the unbounded {@code countsByKey} map (every key's true cumulative rejection count) - see
 * {@link ThrottleHeap}'s Javadoc for why the bounded heap alone can't correctly decide whether a
 * not-yet-tracked key deserves a spot without it.
 */
@Component
public class ThrottledKeyTracker {

    private final Map<String, Long> countsByKey = new HashMap<>();
    private final ThrottleHeap heap;

    public ThrottledKeyTracker(@Value("${ringwatch.rate-limit.top-throttled-size:10}") int topN) {
        this.heap = new ThrottleHeap(topN);
    }

    public synchronized void recordThrottle(String key) {
        long newCount = countsByKey.merge(key, 1L, Long::sum);
        heap.observe(key, newCount);
    }

    public synchronized List<ThrottleCount> topThrottled() {
        return heap.topByCountDescending();
    }
}
