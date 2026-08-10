package com.ringwatch.gateway.ratelimit;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Runs after {@link com.ringwatch.gateway.security.JwtAuthenticationGlobalFilter}, so the
 * X-User-Id header (when present) already reflects verified JWT identity rather than a
 * client-supplied value.
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final SlidingWindowRateLimiter rateLimiter;
    private final ThrottledKeyTracker throttledKeyTracker;

    public RateLimitGlobalFilter(
            @Value("${ringwatch.rate-limit.limit}") int limit,
            @Value("${ringwatch.rate-limit.window}") Duration window,
            ThrottledKeyTracker throttledKeyTracker) {
        this.rateLimiter = new SlidingWindowRateLimiter(limit, window);
        this.throttledKeyTracker = throttledKeyTracker;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String key = resolveKey(exchange);
        if (!rateLimiter.tryAcquire(key)) {
            throttledKeyTracker.recordThrottle(key);
            return reject(exchange);
        }
        return chain.filter(exchange);
    }

    private String resolveKey(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
        if (userId != null) {
            return "user:" + userId;
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return "ip:" + (remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown");
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"message\":\"Rate limit exceeded\"}".getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
