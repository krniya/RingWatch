package com.ringwatch.risk.kafka;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.kafka.Topics;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The AI base URL points at a WireMock instance stubbed to fail by default (see
 * {@link #resetStubsAndCircuitBreaker()}), so unless a test stubs a success response, every
 * scored event here is produced via the rule-based fallback (FR8) rather than the LLM.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {Topics.TRANSACTIONS_ENRICHED, Topics.TRANSACTIONS_SCORED})
class RiskScoringListenerIntegrationTest {

    private static WireMockServer wireMock;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Producer<String, Object> producer;
    private Consumer<String, String> consumer;

    @DynamicPropertySource
    static void registerAiBaseUrl(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        registry.add("ringwatch.ai.base-url", wireMock::baseUrl);
    }

    @AfterAll
    static void tearDownWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubsAndCircuitBreaker() {
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(500)));
        circuitBreakerRegistry.circuitBreaker("llmRiskScorer").reset();

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producer = new DefaultKafkaProducerFactory<String, Object>(
                producerProps, new StringSerializer(), new JsonSerializer<>())
                .createProducer();

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("risk-scoring-test-consumer", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, Topics.TRANSACTIONS_SCORED);
    }

    @AfterEach
    void tearDown() {
        producer.close();
        consumer.close();
    }

    private static EnrichedTransactionEvent event(String transactionId, String accountId) {
        return new EnrichedTransactionEvent(
                transactionId, accountId, "receiver-1", new BigDecimal("500.00"), "USD",
                "device-new", "10.0.0.99", Instant.now().truncatedTo(ChronoUnit.MILLIS),
                0, BigDecimal.ZERO, Set.of(), Set.of());
    }

    @Test
    void enrichedTransactionIsScoredViaRuleBasedFallbackAndPublished() throws Exception {
        String accountId = "acct-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_ENRICHED, accountId, event("tx-1", accountId))).get();
        producer.flush();

        JsonNode scored = objectMapper.readTree(pollForRecordWithKey(accountId).value());
        assertThat(scored.get("transactionId").asText()).isEqualTo("tx-1");
        assertThat(scored.get("scoringMethod").asText()).isEqualTo("RULE_FALLBACK");
        assertThat(scored.get("riskScore").decimalValue()).isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(scored.get("explanation").asText()).contains("no prior transaction history");
    }

    @Test
    void enrichedTransactionIsScoredViaLlmWhenAvailableAndPublished() throws Exception {
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/v1/messages")).willReturn(okJson("""
                {
                  "id": "msg_test1",
                  "type": "message",
                  "role": "assistant",
                  "model": "test-model",
                  "content": [
                    {"type": "text", "text": "{\\"riskScore\\": 0.65, \\"explanation\\": \\"elevated risk\\"}"}
                  ],
                  "stop_reason": "end_turn",
                  "stop_sequence": null,
                  "usage": {"input_tokens": 50, "output_tokens": 20}
                }
                """)));
        String accountId = "acct-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_ENRICHED, accountId, event("tx-2", accountId))).get();
        producer.flush();

        JsonNode scored = objectMapper.readTree(pollForRecordWithKey(accountId).value());
        assertThat(scored.get("transactionId").asText()).isEqualTo("tx-2");
        assertThat(scored.get("scoringMethod").asText()).isEqualTo("AI");
        assertThat(scored.get("riskScore").decimalValue()).isEqualByComparingTo(new BigDecimal("0.65"));
        assertThat(scored.get("explanation").asText()).isEqualTo("elevated risk");
    }

    @Test
    void poisonPillMessageIsSkippedWithoutBlockingSubsequentValidMessages() throws Exception {
        String accountId = "acct-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_ENRICHED, accountId, "not-a-transaction")).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_ENRICHED, accountId, event("tx-after-poison-pill", accountId))).get();
        producer.flush();

        JsonNode scored = objectMapper.readTree(pollForRecordWithKey(accountId).value());
        assertThat(scored.get("transactionId").asText()).isEqualTo("tx-after-poison-pill");
        assertThat(scored.get("scoringMethod").asText()).isEqualTo("RULE_FALLBACK");
    }

    private ConsumerRecord<String, String> pollForRecordWithKey(String key) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 15_000;
        while (matching.isEmpty() && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
            records.records(Topics.TRANSACTIONS_SCORED).forEach(record -> {
                if (key.equals(record.key())) {
                    matching.add(record);
                }
            });
        }
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }
}
