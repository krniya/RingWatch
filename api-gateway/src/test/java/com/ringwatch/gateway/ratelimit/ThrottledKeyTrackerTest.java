package com.ringwatch.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThrottledKeyTrackerTest {

    @Test
    void repeatedThrottlesForTheSameKeyAccumulateIntoOneEntry() {
        ThrottledKeyTracker tracker = new ThrottledKeyTracker(10);

        tracker.recordThrottle("user:acct-1");
        tracker.recordThrottle("user:acct-1");
        tracker.recordThrottle("user:acct-1");

        assertThat(tracker.topThrottled()).containsExactly(new ThrottleCount("user:acct-1", 3));
    }

    @Test
    void differentKeysAreTrackedIndependently() {
        ThrottledKeyTracker tracker = new ThrottledKeyTracker(10);

        tracker.recordThrottle("user:acct-1");
        tracker.recordThrottle("ip:9.9.9.9");
        tracker.recordThrottle("ip:9.9.9.9");

        assertThat(tracker.topThrottled())
                .extracting(ThrottleCount::key)
                .containsExactly("ip:9.9.9.9", "user:acct-1");
    }

    @Test
    void aLateArrivingKeyThatOutpacesTheCurrentTopEventuallyDisplacesIt() {
        ThrottledKeyTracker tracker = new ThrottledKeyTracker(1);
        tracker.recordThrottle("user:acct-1");
        tracker.recordThrottle("user:acct-1");

        tracker.recordThrottle("user:acct-2");
        tracker.recordThrottle("user:acct-2");
        tracker.recordThrottle("user:acct-2");

        assertThat(tracker.topThrottled()).containsExactly(new ThrottleCount("user:acct-2", 3));
    }
}
