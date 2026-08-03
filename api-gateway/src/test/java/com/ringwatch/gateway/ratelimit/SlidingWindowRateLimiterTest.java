package com.ringwatch.gateway.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

    @Test
    void allowsUpToLimitRequestsWithinWindow() {
        AtomicLong clock = new AtomicLong(0);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, Duration.ofSeconds(10), clock::get);

        assertTrue(limiter.tryAcquire("acct-1"));
        assertTrue(limiter.tryAcquire("acct-1"));
        assertTrue(limiter.tryAcquire("acct-1"));
    }

    @Test
    void rejectsRequestsBeyondLimitWithinWindow() {
        AtomicLong clock = new AtomicLong(0);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, Duration.ofSeconds(10), clock::get);

        assertTrue(limiter.tryAcquire("acct-1"));
        assertTrue(limiter.tryAcquire("acct-1"));
        assertFalse(limiter.tryAcquire("acct-1"));
    }

    @Test
    void allowsRequestsAgainOnceOldEntriesSlideOutOfTheWindow() {
        AtomicLong clock = new AtomicLong(0);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofSeconds(10), clock::get);

        assertTrue(limiter.tryAcquire("acct-1"));
        assertFalse(limiter.tryAcquire("acct-1"));

        clock.set(10_001);
        assertTrue(limiter.tryAcquire("acct-1"));
    }

    @Test
    void tracksDifferentKeysIndependently() {
        AtomicLong clock = new AtomicLong(0);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofSeconds(10), clock::get);

        assertTrue(limiter.tryAcquire("acct-1"));
        assertTrue(limiter.tryAcquire("acct-2"));
    }
}
