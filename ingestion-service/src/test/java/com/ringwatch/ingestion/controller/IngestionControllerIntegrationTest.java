package com.ringwatch.ingestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.common.event.TransactionRawEvent;
import com.ringwatch.ingestion.controller.dto.CreateTransactionRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IngestionControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Value("${ringwatch.jwt.secret}")
    private String jwtSecret;

    @MockBean private KafkaTemplate<String, TransactionRawEvent> kafkaTemplate;

    @BeforeEach
    void stubKafkaSend() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    private static CreateTransactionRequest sampleRequest(String transactionId) {
        return new CreateTransactionRequest(
                transactionId, "sender-1", "receiver-1",
                new BigDecimal("100.00"), "USD", "device-1", "127.0.0.1", Instant.now());
    }

    private String validToken() {
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", "test-caller")
                .claim("role", "SERVICE")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(signingKey)
                .compact();
    }

    @Test
    void newTransactionIsAcceptedAndPublished() throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest("tx-100"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value("tx-100"))
                .andExpect(jsonPath("$.duplicate").value(false));

        verify(kafkaTemplate, times(1)).send(any(), any(), any());
    }

    @Test
    void duplicateTransactionIdIsAcceptedIdempotentlyWithoutRepublishing() throws Exception {
        CreateTransactionRequest request = sampleRequest("tx-101");
        String token = validToken();

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        verify(kafkaTemplate, times(1)).send(any(), any(), any());
    }

    @Test
    void missingRequiredFieldsReturnsBadRequest() throws Exception {
        String invalidJson = """
                {"transactionId": "", "amount": -5}
                """;

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json-at-all"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest("tx-no-auth"))))
                .andExpect(status().isForbidden());

        verify(kafkaTemplate, times(0)).send(any(), any(), any());
    }

    @Test
    void requestWithInvalidTokenIsRejected() throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest("tx-bad-auth"))))
                .andExpect(status().isForbidden());
    }
}
