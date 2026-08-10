package com.ringwatch.gateway.ratelimit;

/** One rate-limit key's cumulative throttle count (FR17). */
public record ThrottleCount(String key, long count) {
}
