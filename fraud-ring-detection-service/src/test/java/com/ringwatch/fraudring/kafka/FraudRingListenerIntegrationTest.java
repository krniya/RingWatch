package com.ringwatch.fraudring.kafka;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
 * {@link #resetStubsAndCircuitBreaker()}), so every explanation here comes from
 * {@code RingExplainer}'s templated fallback rather than a real LLM call.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {Topics.TRANSACTIONS_ENRICHED, Topics.TRANSACTIONS_RING_FLAGGED})
class FraudRingListenerIntegrationTest {

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

    @BeforeEach
    void resetStubsAndCircuitBreaker() {
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(500)));
        circuitBreakerRegistry.circuitBreaker("ringExplainer").reset();

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producer = new DefaultKafkaProducerFactory<String, Object>(
                producerProps, new StringSerializer(), new JsonSerializer<>())
                .createProducer();

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("fraud-ring-test-consumer-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, Topics.TRANSACTIONS_RING_FLAGGED);
    }

    @AfterEach
    void tearDown() {
        producer.close();
        consumer.close();
    }

    private static EnrichedTransactionEvent event(
            String transactionId, String sender, String receiver, String deviceId, String ipAddress) {
        return new EnrichedTransactionEvent(
                transactionId, sender, receiver, new BigDecimal("100.00"), "USD",
                deviceId, ipAddress, Instant.now().truncatedTo(ChronoUnit.MILLIS),
                1, BigDecimal.TEN, Set.of(), Set.of());
    }

    @Test
    void clusterCrossingThresholdViaSharedDevicePublishesRingFlaggedEvent() throws Exception {
        String a = "acct-" + UUID.randomUUID();
        String b = "acct-" + UUID.randomUUID();
        String c = "acct-" + UUID.randomUUID();
        String d = "acct-" + UUID.randomUUID();
        String sharedDevice = "dev-" + UUID.randomUUID();

        // AccountClusterGraph is a singleton bean shared across every test in this class, so IPs
        // (like account IDs and devices) must be unique per test - reusing one would silently
        // merge unrelated tests' accounts into a single cluster via the shared-IP union.
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_ENRICHED, a,
                event("tx-1", a, b, sharedDevice, "ip-" + UUID.randomUUID()))).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_ENRICHED, c,
                event("tx-2", c, d, sharedDevice, "ip-" + UUID.randomUUID()))).get();
        producer.flush();

        JsonNode ring = objectMapper.readTree(pollForRecordContainingAccount(a).value());
        assertThat(memberAccountIds(ring)).containsExactlyInAnyOrder(a, b, c, d);
        assertThat(ring.get("sharedAttributes").asText()).contains(sharedDevice);
        assertThat(ring.get("aiExplanation").asText()).contains(a, b, c, d);
    }

    @Test
    void cycleDetectedViaPingPongTransfersPublishesRingFlaggedEvent() throws Exception {
        String a = "acct-" + UUID.randomUUID();
        String b = "acct-" + UUID.randomUUID();
        String uniqueDevice1 = "dev-" + UUID.randomUUID();
        String uniqueDevice2 = "dev-" + UUID.randomUUID();
        String uniqueIp = "ip-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_ENRICHED, a, event("tx-1", a, b, uniqueDevice1, uniqueIp))).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_ENRICHED, b, event("tx-2", b, a, uniqueDevice2, uniqueIp))).get();
        producer.flush();

        JsonNode ring = objectMapper.readTree(pollForRecordContainingAccount(a).value());
        assertThat(memberAccountIds(ring)).containsExactlyInAnyOrder(a, b);
        assertThat(ring.get("sharedAttributes").asText()).contains("Circular fund movement");
    }

    @Test
    void poisonPillMessageIsSkippedWithoutBlockingSubsequentValidMessages() throws Exception {
        String a = "acct-" + UUID.randomUUID();
        String b = "acct-" + UUID.randomUUID();
        String uniqueIp = "ip-" + UUID.randomUUID();
        String uniqueDevice1 = "dev-" + UUID.randomUUID();
        String uniqueDevice2 = "dev-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_ENRICHED, a, "not-a-transaction")).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_ENRICHED, a, event("tx-1", a, b, uniqueDevice1, uniqueIp))).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_ENRICHED, b, event("tx-2", b, a, uniqueDevice2, uniqueIp))).get();
        producer.flush();

        JsonNode ring = objectMapper.readTree(pollForRecordContainingAccount(a).value());
        assertThat(memberAccountIds(ring)).containsExactlyInAnyOrder(a, b);
    }

    private static List<String> memberAccountIds(JsonNode ring) {
        List<String> members = new ArrayList<>();
        ring.get("memberAccountIds").forEach(node -> members.add(node.asText()));
        return members;
    }

    private ConsumerRecord<String, String> pollForRecordContainingAccount(String accountId) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 15_000;
        while (matching.isEmpty() && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
            records.records(Topics.TRANSACTIONS_RING_FLAGGED).forEach(record -> {
                try {
                    JsonNode value = objectMapper.readTree(record.value());
                    if (memberAccountIds(value).contains(accountId)) {
                        matching.add(record);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }
}
