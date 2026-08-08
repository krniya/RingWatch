package com.ringwatch.audit.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringwatch.audit.model.AuditEventType;
import com.ringwatch.audit.model.AuditLog;
import com.ringwatch.audit.repository.AuditLogRepository;
import com.ringwatch.common.kafka.Topics;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        topics = {Topics.TRANSACTIONS_RAW, Topics.TRANSACTIONS_SCORED, Topics.TRANSACTIONS_DECIDED})
class AuditControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Value("${ringwatch.jwt.secret}")
    private String jwtSecret;

    private String validToken() {
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", "test-analyst")
                .claim("role", "ANALYST")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(signingKey)
                .compact();
    }

    @BeforeEach
    void clearAuditLog() {
        auditLogRepository.deleteAll();
    }

    @Test
    void byTransactionIdReturnsEveryEntryForThatTransactionOrdered() throws Exception {
        auditLogRepository.save(new AuditLog("tx-1", AuditEventType.CREATED, "{\"a\":1}", null));
        auditLogRepository.save(new AuditLog("tx-1", AuditEventType.DECIDED, "{\"b\":2}", null));
        auditLogRepository.save(new AuditLog("tx-2", AuditEventType.CREATED, "{\"c\":3}", null));

        mockMvc.perform(get("/audit/tx-1").header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventType").value("CREATED"))
                .andExpect(jsonPath("$[0].payload.a").value(1))
                .andExpect(jsonPath("$[1].eventType").value("DECIDED"));
    }

    @Test
    void unknownTransactionIdReturnsAnEmptyList() throws Exception {
        mockMvc.perform(get("/audit/no-such-tx").header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchFiltersByUserId() throws Exception {
        auditLogRepository.save(new AuditLog("tx-1", AuditEventType.OVERRIDDEN, "{}", "analyst-1"));
        auditLogRepository.save(new AuditLog("tx-2", AuditEventType.OVERRIDDEN, "{}", "analyst-2"));

        mockMvc.perform(get("/audit").param("userId", "analyst-1").header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value("tx-1"));
    }

    @Test
    void searchWithNoFiltersReturnsEveryEntry() throws Exception {
        auditLogRepository.save(new AuditLog("tx-1", AuditEventType.CREATED, "{}", null));
        auditLogRepository.save(new AuditLog("tx-2", AuditEventType.CREATED, "{}", null));

        mockMvc.perform(get("/audit").header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/audit/tx-1")).andExpect(status().isForbidden());
    }

    @Test
    void requestWithInvalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/audit/tx-1").header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithBlankBearerTokenIsRejectedNotErrored() throws Exception {
        mockMvc.perform(get("/audit/tx-1").header("Authorization", "Bearer "))
                .andExpect(status().isForbidden());
    }
}
