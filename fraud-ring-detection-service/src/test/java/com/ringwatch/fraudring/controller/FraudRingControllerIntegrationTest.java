package com.ringwatch.fraudring.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringwatch.common.kafka.Topics;
import com.ringwatch.fraudring.model.FraudRingDetection;
import com.ringwatch.fraudring.repository.FraudRingDetectionRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;

/**
 * This test only exercises the JPA read path ({@code GET /fraud-rings}), not
 * {@code FraudRingDetectionService.publishRing()}'s write+publish path (already covered by
 * {@code FraudRingDetectionServiceTest}) - but {@code @EmbeddedKafka} is still required: the
 * service's existing real {@code FraudRingListener} bean boots in this context regardless, and it
 * needs a broker to resolve {@code ${spring.embedded.kafka.brokers}} against (same issue
 * decision-engine's controller test hit when adding JPA/security to a module that already had
 * Kafka listeners sharing the context). Deliberately NOT {@code @Transactional}: each MockMvc call
 * should go through a real, separate, committed request/session the way production traffic does
 * (this module runs with {@code open-in-view: false}) - wrapping the whole test in one transaction
 * would keep a single Hibernate session open across the test and silently mask lazy-loading bugs
 * that only manifest once the session is genuinely closed after the request. Isolation between
 * tests instead comes from an explicit {@code @AfterEach} cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@EmbeddedKafka(partitions = 1, topics = {Topics.TRANSACTIONS_ENRICHED, Topics.TRANSACTIONS_RING_FLAGGED})
class FraudRingControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private FraudRingDetectionRepository fraudRingDetectionRepository;

    @Value("${ringwatch.jwt.secret}")
    private String jwtSecret;

    @AfterEach
    void tearDown() {
        fraudRingDetectionRepository.deleteAll();
    }

    private String validToken() {
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
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

    @Test
    void listReturnsEmptyWhenNoDetectionsExist() throws Exception {
        mockMvc.perform(get("/fraud-rings").header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listReturnsPersistedDetectionsNewestFirst() throws Exception {
        Instant older = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant newer = Instant.now();
        fraudRingDetectionRepository.save(new FraudRingDetection(
                "ring-old", Set.of("A", "B"), "shared device 'dev1'", "explanation-old", older));
        fraudRingDetectionRepository.save(new FraudRingDetection(
                "ring-new", Set.of("B", "C"), "shared IP '1.1.1.1'", "explanation-new", newer));

        mockMvc.perform(get("/fraud-rings").header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ringId").value("ring-new"))
                .andExpect(jsonPath("$[0].memberAccountIds", org.hamcrest.Matchers.containsInAnyOrder("B", "C")))
                .andExpect(jsonPath("$[1].ringId").value("ring-old"));
    }

    @Test
    void listWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/fraud-rings")).andExpect(status().isForbidden());
    }

    @Test
    void listWithInvalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/fraud-rings").header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actuatorHealthAndPrometheusEndpointsAreAccessibleWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }
}
