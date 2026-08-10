package com.ringwatch.gateway.ratelimit;

import java.util.List;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * FR17: exposes the current top-throttled-keys view for monitoring, alongside the existing
 * {@code /actuator/health}/{@code /actuator/prometheus} endpoints. First custom Actuator endpoint
 * in this repo - {@code @Endpoint}/{@code @ReadOperation} are transport-agnostic, so this needs no
 * reactive-specific handling despite api-gateway being a pure WebFlux app.
 *
 * <p><b>Known, accepted tradeoff:</b> like every other actuator endpoint in this system, this is
 * unauthenticated - api-gateway has no Spring Security filter chain of its own, and its
 * {@code GlobalFilter}s (JWT validation, rate limiting) never run for {@code /actuator/**}
 * requests. That means an anonymous caller can see which account IDs (opaque UUIDs) and client
 * IPs have recently been rate-limited. Building real per-endpoint auth for just this one
 * operator-facing monitoring endpoint would mean introducing Spring Security into a WebFlux app
 * that has none today - a disproportionately large addition for this feature. Treated as
 * consistent with this repo's existing "actuator endpoints are operator-only, reachable only by
 * whoever can reach the gateway" trust model, not a gap unique to this endpoint.
 */
@Component
@Endpoint(id = "throttled-accounts")
public class ThrottledAccountsEndpoint {

    private final ThrottledKeyTracker tracker;

    public ThrottledAccountsEndpoint(ThrottledKeyTracker tracker) {
        this.tracker = tracker;
    }

    @ReadOperation
    public List<ThrottleCount> throttledAccounts() {
        return tracker.topThrottled();
    }
}
