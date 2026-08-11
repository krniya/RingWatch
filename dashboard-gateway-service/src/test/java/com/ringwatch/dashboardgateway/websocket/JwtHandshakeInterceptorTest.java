package com.ringwatch.dashboardgateway.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ringwatch.common.security.AuthenticatedPrincipal;
import com.ringwatch.common.security.JwtValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class JwtHandshakeInterceptorTest {

    private static final String SECRET = "test-only-secret-key-at-least-32-bytes-long-for-hs256";

    private final JwtValidator jwtValidator = new JwtValidator(SECRET);
    private final JwtHandshakeInterceptor interceptor = new JwtHandshakeInterceptor(jwtValidator);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);

    private static String validToken() {
        SecretKey signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", "alice")
                .claim("role", "ANALYST")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(signingKey)
                .compact();
    }

    private static ServerHttpRequest requestWithUri(String uri) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }

    @Test
    void validTokenInQueryParamAllowsTheHandshakeAndStoresThePrincipal() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = requestWithUri("ws://localhost:8091/ws/alerts?token=" + validToken());

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes.get(JwtHandshakeInterceptor.PRINCIPAL_ATTRIBUTE))
                .isInstanceOfSatisfying(AuthenticatedPrincipal.class, p -> assertThat(p.username()).isEqualTo("alice"));
    }

    @Test
    void missingTokenRejectsTheHandshake() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = requestWithUri("ws://localhost:8091/ws/alerts");

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(allowed).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidTokenRejectsTheHandshake() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = requestWithUri("ws://localhost:8091/ws/alerts?token=not-a-valid-jwt");

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(allowed).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
