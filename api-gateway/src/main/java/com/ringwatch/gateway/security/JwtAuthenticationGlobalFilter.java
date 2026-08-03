package com.ringwatch.gateway.security;

import com.ringwatch.common.security.AuthenticatedPrincipal;
import com.ringwatch.common.security.JwtValidator;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> OPEN_PATHS = List.of("/auth/login");
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final JwtValidator jwtValidator;

    public JwtAuthenticationGlobalFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Strip any client-supplied identity headers unconditionally, on every path (including
        // open ones), so a caller can never smuggle a spoofed X-User-Id/X-User-Role downstream.
        ServerHttpRequest strippedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();
        ServerWebExchange strippedExchange = exchange.mutate().request(strippedRequest).build();

        if (OPEN_PATHS.contains(strippedRequest.getURI().getPath())) {
            return chain.filter(strippedExchange);
        }

        String header = strippedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(exchange);
        }

        try {
            AuthenticatedPrincipal principal = jwtValidator.validate(header.substring(BEARER_PREFIX.length()));

            ServerHttpRequest authenticatedRequest = strippedRequest.mutate()
                    .headers(headers -> {
                        headers.add(USER_ID_HEADER, principal.accountId());
                        headers.add(USER_ROLE_HEADER, principal.role());
                    })
                    .build();

            return chain.filter(strippedExchange.mutate().request(authenticatedRequest).build());
        } catch (JwtException | IllegalArgumentException e) {
            return reject(exchange);
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"message\":\"Missing or invalid authentication token\"}".getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
