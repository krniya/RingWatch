package com.ringwatch.decision.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.decision.model.Decision;
import com.ringwatch.decision.repository.DecisionRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real embedded broker, not {@code @MockBean KafkaTemplate} - decision-engine's context also
 * boots its existing {@code @KafkaListener}s (DecisionListener/RingFlagListener), and combining
 * {@code @EmbeddedKafka} with a mocked KafkaTemplate breaks the embedded broker's property
 * placeholder resolution. Mirrors {@code DecisionListenerIntegrationTest}'s real-broker style
 * instead of {@code IngestionControllerIntegrationTest}'s mocked style, since this module (unlike
 * ingestion-service) has consumers sharing the same context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@EmbeddedKafka(partitions = 1,
        topics = {Topics.TRANSACTIONS_SCORED, Topics.TRANSACTIONS_RING_FLAGGED, Topics.TRANSACTIONS_OVERRIDDEN})
class DecisionControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DecisionRepository decisionRepository;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${ringwatch.jwt.secret}")
    private String jwtSecret;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("decision-engine-override-test-consumer", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, Topics.TRANSACTIONS_OVERRIDDEN);
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    private String validToken(String username) {
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", username)
                .claim("role", "ANALYST")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(signingKey)
                .compact();
    }

    private static String overrideBody(String outcome, String reason) {
        return """
                {"outcome": "%s", "reason": "%s"}
                """.formatted(outcome, reason);
    }

    private ConsumerRecord<String, String> pollForRecordWithKey(String key) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records.records(Topics.TRANSACTIONS_OVERRIDDEN)) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No record with key '" + key + "' arrived on " + Topics.TRANSACTIONS_OVERRIDDEN);
    }

    @Test
    void overridingAnExistingDecisionUpdatesItAndPublishesAnOverriddenEvent() throws Exception {
        decisionRepository.save(new Decision("tx-200", DecisionOutcome.BLOCK, "originally blocked"));

        mockMvc.perform(post("/transactions/tx-200/override")
                        .header("Authorization", "Bearer " + validToken("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody("APPROVE", "confirmed legitimate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("tx-200"))
                .andExpect(jsonPath("$.outcome").value("APPROVE"))
                .andExpect(jsonPath("$.overriddenBy").value("alice"))
                .andExpect(jsonPath("$.overrideReason").value("confirmed legitimate"));

        Decision updated = decisionRepository.findByTransactionId("tx-200").orElseThrow();
        assertThat(updated.getOutcome()).isEqualTo(DecisionOutcome.APPROVE);

        JsonNode published = objectMapper.readTree(pollForRecordWithKey("tx-200").value());
        assertThat(published.get("outcome").asText()).isEqualTo("APPROVE");
        assertThat(published.get("overriddenBy").asText()).isEqualTo("alice");
        assertThat(published.get("overrideReason").asText()).isEqualTo("confirmed legitimate");
    }

    @Test
    void overridingAnUnknownTransactionReturnsNotFound() throws Exception {
        mockMvc.perform(post("/transactions/tx-does-not-exist/override")
                        .header("Authorization", "Bearer " + validToken("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody("APPROVE", "reason")))
                .andExpect(status().isNotFound());
    }

    @Test
    void overrideMissingReasonReturnsBadRequest() throws Exception {
        decisionRepository.save(new Decision("tx-201", DecisionOutcome.FLAG, "originally flagged"));

        mockMvc.perform(post("/transactions/tx-201/override")
                        .header("Authorization", "Bearer " + validToken("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "APPROVE", "reason": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overrideWithoutTokenIsRejected() throws Exception {
        decisionRepository.save(new Decision("tx-202", DecisionOutcome.BLOCK, "originally blocked"));

        mockMvc.perform(post("/transactions/tx-202/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody("APPROVE", "reason")))
                .andExpect(status().isForbidden());
    }

    @Test
    void overrideWithInvalidTokenIsRejected() throws Exception {
        decisionRepository.save(new Decision("tx-203", DecisionOutcome.BLOCK, "originally blocked"));

        mockMvc.perform(post("/transactions/tx-203/override")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody("APPROVE", "reason")))
                .andExpect(status().isForbidden());
    }

    @Test
    void actuatorHealthAndPrometheusEndpointsAreAccessibleWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }
}
