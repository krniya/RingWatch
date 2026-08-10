package com.ringwatch.risk.kafka;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ringwatch.common.event.ReconciliationScoreRequest;
import com.ringwatch.common.kafka.Topics;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
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

/** FR24: verifies the isolated re-scoring path never touches the real transactions.scored topic. */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
        Topics.TRANSACTIONS_ENRICHED, Topics.TRANSACTIONS_SCORED,
        Topics.TRANSACTIONS_RECONCILIATION_SCORING_REQUESTED, Topics.TRANSACTIONS_RECONCILIATION_SCORED})
class ReconciliationScoringListenerIntegrationTest {

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
                KafkaTestUtils.consumerProps("reconciliation-scoring-test-consumer", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        embeddedKafkaBroker.consumeFromEmbeddedTopics(
                consumer, Topics.TRANSACTIONS_RECONCILIATION_SCORED, Topics.TRANSACTIONS_SCORED);
    }

    @AfterEach
    void tearDown() {
        producer.close();
        consumer.close();
    }

    private static ReconciliationScoreRequest request(String correlationId, String originalTransactionId) {
        return new ReconciliationScoreRequest(
                correlationId, originalTransactionId, "acct-sender", "acct-receiver",
                new BigDecimal("500.00"), "USD", "device-old", "10.0.0.1",
                Instant.now().truncatedTo(ChronoUnit.MILLIS), 3, new BigDecimal("100.00"),
                Set.of("device-old"), Set.of("10.0.0.1"));
    }

    @Test
    void reconciliationRequestIsScoredAndPublishedWithoutTouchingTheRealScoredTopic() throws Exception {
        String correlationId = "recon-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_RECONCILIATION_SCORING_REQUESTED, correlationId,
                request(correlationId, "tx-original-1"))).get();
        producer.flush();

        JsonNode scored = pollForReconciliationScored(correlationId);
        assertThat(scored.get("originalTransactionId").asText()).isEqualTo("tx-original-1");
        assertThat(scored.get("scoringMethod").asText()).isEqualTo("RULE_FALLBACK");

        ConsumerRecords<String, String> realTopicRecords = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
        assertThat(realTopicRecords.records(Topics.TRANSACTIONS_SCORED)).isEmpty();
    }

    private JsonNode pollForReconciliationScored(String correlationId) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
            for (org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record
                    : records.records(Topics.TRANSACTIONS_RECONCILIATION_SCORED)) {
                if (correlationId.equals(record.key())) {
                    return objectMapper.readTree(record.value());
                }
            }
        }
        throw new AssertionError("No reconciliation-scored record with key '" + correlationId + "' arrived");
    }
}
