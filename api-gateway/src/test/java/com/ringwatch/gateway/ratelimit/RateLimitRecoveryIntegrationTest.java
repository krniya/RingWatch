package com.ringwatch.gateway.ratelimit;

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

/**
 * Separate Spring context from {@link RateLimitGlobalFilterIntegrationTest} because it needs its
 * own short window, which the other test class's much longer window would make impractical to
 * verify without an equally long sleep.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RateLimitRecoveryIntegrationTest {

    private static final HttpServer STUB = startStub();

    @Autowired private WebTestClient webTestClient;

    @Value("${ringwatch.jwt.secret}")
    private String jwtSecret;

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/transactions", exchange -> {
                byte[] bytes = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
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

        registry.add("ringwatch.rate-limit.limit", () -> "1");
        registry.add("ringwatch.rate-limit.window", () -> "500ms");
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
    void callerCanSucceedAgainOnceTheWindowSlidesPastTheEarlierRequest() throws InterruptedException {
        String token = "Bearer " + validToken("recovery-test-account");

        webTestClient.get().uri("/transactions").header("Authorization", token).exchange().expectStatus().isOk();
        webTestClient.get().uri("/transactions").header("Authorization", token).exchange()
                .expectStatus().isEqualTo(429);

        Thread.sleep(600);

        webTestClient.get().uri("/transactions").header("Authorization", token).exchange().expectStatus().isOk();
    }
}
