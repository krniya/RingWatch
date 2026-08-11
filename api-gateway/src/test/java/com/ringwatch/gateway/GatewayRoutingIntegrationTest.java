package com.ringwatch.gateway;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
class GatewayRoutingIntegrationTest {

    private static final AtomicReference<Headers> LAST_AUTH_REQUEST_HEADERS = new AtomicReference<>();
    private static final AtomicReference<Headers> LAST_INGESTION_REQUEST_HEADERS = new AtomicReference<>();
    private static final AtomicReference<Headers> LAST_AUDIT_REQUEST_HEADERS = new AtomicReference<>();
    private static final AtomicReference<Headers> LAST_OVERRIDE_REQUEST_HEADERS = new AtomicReference<>();
    private static final AtomicReference<Headers> LAST_FRAUD_RINGS_REQUEST_HEADERS = new AtomicReference<>();
    private static final AtomicReference<Headers> LAST_WS_ALERTS_REQUEST_HEADERS = new AtomicReference<>();

    private static final HttpServer AUTH_STUB =
            startStub("/auth/login", "auth-stub-response", LAST_AUTH_REQUEST_HEADERS);
    private static final HttpServer INGESTION_STUB =
            startStub("/transactions", "ingestion-stub-response", LAST_INGESTION_REQUEST_HEADERS);
    private static final HttpServer AUDIT_STUB =
            startStub("/audit", "audit-stub-response", LAST_AUDIT_REQUEST_HEADERS);
    private static final HttpServer DECISION_OVERRIDE_STUB =
            startStub("/transactions", "override-stub-response", LAST_OVERRIDE_REQUEST_HEADERS);
    private static final HttpServer FRAUD_RINGS_STUB =
            startStub("/fraud-rings", "fraud-rings-stub-response", LAST_FRAUD_RINGS_REQUEST_HEADERS);
    private static final HttpServer WS_ALERTS_STUB =
            startStub("/ws/alerts", "ws-alerts-stub-response", LAST_WS_ALERTS_REQUEST_HEADERS);

    @Autowired private WebTestClient webTestClient;

    @Value("${ringwatch.jwt.secret}")
    private String jwtSecret;

    private static HttpServer startStub(String path, String responseBody, AtomicReference<Headers> capturedHeaders) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext(path, exchange -> {
                if (capturedHeaders != null) {
                    capturedHeaders.set(exchange.getRequestHeaders());
                }
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
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
    static void routeToStubs(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].id", () -> "auth-service");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> "http://localhost:" + AUTH_STUB.getAddress().getPort());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/auth/**");

        // Must precede the ingestion-service route below (first-match-wins), same as in
        // application.yml, or the general /transactions/** predicate would swallow this first.
        registry.add("spring.cloud.gateway.routes[1].id", () -> "decision-engine-override");
        registry.add("spring.cloud.gateway.routes[1].uri",
                () -> "http://localhost:" + DECISION_OVERRIDE_STUB.getAddress().getPort());
        registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/transactions/*/override");
        registry.add("spring.cloud.gateway.routes[1].predicates[1]", () -> "Method=POST");

        registry.add("spring.cloud.gateway.routes[2].id", () -> "ingestion-service");
        registry.add("spring.cloud.gateway.routes[2].uri", () -> "http://localhost:" + INGESTION_STUB.getAddress().getPort());
        registry.add("spring.cloud.gateway.routes[2].predicates[0]", () -> "Path=/transactions/**");

        registry.add("spring.cloud.gateway.routes[3].id", () -> "audit-service");
        registry.add("spring.cloud.gateway.routes[3].uri", () -> "http://localhost:" + AUDIT_STUB.getAddress().getPort());
        registry.add("spring.cloud.gateway.routes[3].predicates[0]", () -> "Path=/audit/**");

        registry.add("spring.cloud.gateway.routes[4].id", () -> "fraud-ring-detection-service");
        registry.add("spring.cloud.gateway.routes[4].uri",
                () -> "http://localhost:" + FRAUD_RINGS_STUB.getAddress().getPort());
        registry.add("spring.cloud.gateway.routes[4].predicates[0]", () -> "Path=/fraud-rings/**");

        // Plain http:// here (not ws://, unlike the real application.yml route) since this test
        // only exercises the JwtAuthenticationGlobalFilter's OPEN_PATHS bypass via a normal GET,
        // not a real WebSocket upgrade - Spring Cloud Gateway's own ws:// proxying is an
        // already-established framework feature this test isn't re-proving.
        registry.add("spring.cloud.gateway.routes[5].id", () -> "dashboard-gateway-service");
        registry.add("spring.cloud.gateway.routes[5].uri",
                () -> "http://localhost:" + WS_ALERTS_STUB.getAddress().getPort());
        registry.add("spring.cloud.gateway.routes[5].predicates[0]", () -> "Path=/ws/alerts");
    }

    @AfterAll
    static void stopStubs() {
        AUTH_STUB.stop(0);
        INGESTION_STUB.stop(0);
        AUDIT_STUB.stop(0);
        DECISION_OVERRIDE_STUB.stop(0);
        FRAUD_RINGS_STUB.stop(0);
        WS_ALERTS_STUB.stop(0);
    }

    private String validToken() {
        return tokenSignedWith(jwtSecret, new Date(System.currentTimeMillis() + 3600_000));
    }

    private String expiredToken() {
        return tokenSignedWith(jwtSecret, new Date(System.currentTimeMillis() - 1_000));
    }

    private String tokenSignedWithWrongKey() {
        return tokenSignedWith("a-completely-different-secret-key-at-least-32-bytes-long",
                new Date(System.currentTimeMillis() + 3600_000));
    }

    private String tokenSignedWith(String secret, Date expiration) {
        SecretKey signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", "test-caller")
                .claim("role", "SERVICE")
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    @Test
    void authLoginIsRoutedWithoutRequiringAToken() {
        webTestClient.get().uri("/auth/login")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("auth-stub-response");
    }

    @Test
    void authLoginStripsClientSuppliedIdentityHeadersBeforeForwarding() {
        webTestClient.get().uri("/auth/login")
                .header("X-User-Id", "spoofed-account")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        Headers forwarded = LAST_AUTH_REQUEST_HEADERS.get();
        if (forwarded.getFirst("X-User-Id") != null || forwarded.getFirst("X-User-Role") != null) {
            throw new AssertionError("Expected spoofed identity headers to be stripped but got " + forwarded);
        }
    }

    @Test
    void transactionsRouteAcceptsAValidTokenAndForwardsIdentityHeaders() {
        webTestClient.get().uri("/transactions")
                .header("Authorization", "Bearer " + validToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ingestion-stub-response");

        Headers forwarded = LAST_INGESTION_REQUEST_HEADERS.get();
        assertThatHeaderHasSingleValue(forwarded, "X-User-Role", "SERVICE");
    }

    @Test
    void transactionsRouteRejectsRequestWithoutToken() {
        webTestClient.get().uri("/transactions")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void transactionsRouteRejectsInvalidToken() {
        webTestClient.get().uri("/transactions")
                .header("Authorization", "Bearer not-a-valid-jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void transactionsRouteRejectsBlankBearerToken() {
        webTestClient.get().uri("/transactions")
                .header("Authorization", "Bearer ")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void transactionsRouteRejectsExpiredToken() {
        webTestClient.get().uri("/transactions")
                .header("Authorization", "Bearer " + expiredToken())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void transactionsRouteRejectsTokenSignedWithWrongKey() {
        webTestClient.get().uri("/transactions")
                .header("Authorization", "Bearer " + tokenSignedWithWrongKey())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void auditRouteAcceptsAValidTokenAndForwardsIdentityHeaders() {
        webTestClient.get().uri("/audit")
                .header("Authorization", "Bearer " + validToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("audit-stub-response");

        Headers forwarded = LAST_AUDIT_REQUEST_HEADERS.get();
        assertThatHeaderHasSingleValue(forwarded, "X-User-Role", "SERVICE");
    }

    @Test
    void auditRouteRejectsRequestWithoutToken() {
        webTestClient.get().uri("/audit")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void overrideRouteAcceptsAValidTokenAndForwardsIdentityHeaders() {
        webTestClient.post().uri("/transactions/tx-1/override")
                .header("Authorization", "Bearer " + validToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("override-stub-response");

        Headers forwarded = LAST_OVERRIDE_REQUEST_HEADERS.get();
        assertThatHeaderHasSingleValue(forwarded, "X-User-Role", "SERVICE");
    }

    @Test
    void overrideRouteRejectsRequestWithoutToken() {
        webTestClient.post().uri("/transactions/tx-1/override")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void plainTransactionsPostStillRoutesToIngestionServiceNotDecisionEngine() {
        // Route-ordering regression guard: the override route's Path=/transactions/*/override
        // predicate must not accidentally swallow a plain POST /transactions.
        webTestClient.post().uri("/transactions")
                .header("Authorization", "Bearer " + validToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ingestion-stub-response");
    }

    @Test
    void fraudRingsRouteAcceptsAValidTokenAndForwardsIdentityHeaders() {
        webTestClient.get().uri("/fraud-rings")
                .header("Authorization", "Bearer " + validToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("fraud-rings-stub-response");

        Headers forwarded = LAST_FRAUD_RINGS_REQUEST_HEADERS.get();
        assertThatHeaderHasSingleValue(forwarded, "X-User-Role", "SERVICE");
    }

    @Test
    void fraudRingsRouteRejectsRequestWithoutToken() {
        webTestClient.get().uri("/fraud-rings")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void webSocketAlertsRouteIsOpenAndDoesNotRequireAToken() {
        webTestClient.get().uri("/ws/alerts")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ws-alerts-stub-response");
    }

    private static void assertThatHeaderHasSingleValue(Headers headers, String name, String expectedValue) {
        if (headers == null || !expectedValue.equals(headers.getFirst(name))) {
            throw new AssertionError("Expected header " + name + "=" + expectedValue + " but was " + headers);
        }
    }
}
