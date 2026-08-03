package com.ringwatch.gateway.ratelimit;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RateLimitGlobalFilterIntegrationTest {

    private static final HttpServer STUB = startStub();

    @Autowired private WebTestClient webTestClient;

    @Value("${ringwatch.jwt.secret}")
    private String jwtSecret;

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            HttpHandler okHandler = exchange -> {
                byte[] bytes = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            };
            server.createContext("/transactions", okHandler);
            server.createContext("/auth/login", okHandler);
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void routeAndLimitConfig(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].id", () -> "ingestion-service");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> "http://localhost:" + STUB.getAddress().getPort());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/transactions/**");

        registry.add("spring.cloud.gateway.routes[1].id", () -> "auth-service");
        registry.add("spring.cloud.gateway.routes[1].uri", () -> "http://localhost:" + STUB.getAddress().getPort());
        registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/auth/**");

        registry.add("ringwatch.rate-limit.limit", () -> "2");
        registry.add("ringwatch.rate-limit.window", () -> "60s");
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    private String validToken(String accountId) {
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(accountId)
                .claim("username", "test-caller")
                .claim("role", "SERVICE")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(signingKey)
                .compact();
    }

    @Test
    void thirdRequestWithinWindowIsRateLimited() {
        String token = "Bearer " + validToken("rate-limit-test-account");

        webTestClient.get().uri("/transactions").header("Authorization", token).exchange().expectStatus().isOk();
        webTestClient.get().uri("/transactions").header("Authorization", token).exchange().expectStatus().isOk();
        webTestClient.get().uri("/transactions").header("Authorization", token).exchange()
                .expectStatus().isEqualTo(429)
                .expectBody().jsonPath("$.message").isEqualTo("Rate limit exceeded");
    }

    @Test
    void unauthenticatedRequestsAreRateLimitedByClientIp() {
        webTestClient.get().uri("/auth/login").exchange().expectStatus().isOk();
        webTestClient.get().uri("/auth/login").exchange().expectStatus().isOk();
        webTestClient.get().uri("/auth/login").exchange().expectStatus().isEqualTo(429);
    }

    @Test
    void differentAccountsAreRateLimitedIndependently() {
        String tokenA = "Bearer " + validToken("account-a");
        String tokenB = "Bearer " + validToken("account-b");

        webTestClient.get().uri("/transactions").header("Authorization", tokenA).exchange().expectStatus().isOk();
        webTestClient.get().uri("/transactions").header("Authorization", tokenA).exchange().expectStatus().isOk();
        webTestClient.get().uri("/transactions").header("Authorization", tokenA).exchange()
                .expectStatus().isEqualTo(429);

        webTestClient.get().uri("/transactions").header("Authorization", tokenB).exchange().expectStatus().isOk();
    }
}
