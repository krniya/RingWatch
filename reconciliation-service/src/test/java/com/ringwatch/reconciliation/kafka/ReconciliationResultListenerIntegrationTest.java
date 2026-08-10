package com.ringwatch.reconciliation.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.event.ReconciliationDecisionResult;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.reconciliation.correlation.CorrelationStore;
import com.ringwatch.reconciliation.correlation.PendingReconciliation;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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

@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {Topics.TRANSACTIONS_RECONCILIATION_DECIDED, Topics.TRANSACTIONS_RECONCILED})
class ReconciliationResultListenerIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private CorrelationStore correlationStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Producer<String, Object> producer;
    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producer = new DefaultKafkaProducerFactory<String, Object>(
                producerProps, new StringSerializer(), new JsonSerializer<>())
                .createProducer();

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("reconciliation-result-test-consumer", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, Topics.TRANSACTIONS_RECONCILED);
    }

    @AfterEach
    void tearDown() {
        producer.close();
        consumer.close();
    }

    private static ReconciliationDecisionResult decisionResult(String correlationId, String originalTransactionId, DecisionOutcome newOutcome) {
        return new ReconciliationDecisionResult(
                correlationId, originalTransactionId, newOutcome, "new reason",
                new BigDecimal("0.30"), Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void matchingOutcomePublishesAReconciliationResultWithDriftedFalse() throws Exception {
        String correlationId = "recon-" + UUID.randomUUID();
        correlationStore.put(correlationId, new PendingReconciliation(
                "tx-original-1", DecisionOutcome.APPROVE, new BigDecimal("0.10"), "original reason", Instant.now()));

        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_RECONCILIATION_DECIDED, correlationId,
                decisionResult(correlationId, "tx-original-1", DecisionOutcome.APPROVE))).get();
        producer.flush();

        JsonNode result = pollForRecordWithKey("tx-original-1");
        assertThat(result.get("originalOutcome").asText()).isEqualTo("APPROVE");
        assertThat(result.get("newOutcome").asText()).isEqualTo("APPROVE");
        assertThat(result.get("drifted").asBoolean()).isFalse();
    }

    @Test
    void mismatchingOutcomePublishesAReconciliationResultWithDriftedTrue() throws Exception {
        String correlationId = "recon-" + UUID.randomUUID();
        correlationStore.put(correlationId, new PendingReconciliation(
                "tx-original-2", DecisionOutcome.APPROVE, new BigDecimal("0.10"), "original reason", Instant.now()));

        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_RECONCILIATION_DECIDED, correlationId,
                decisionResult(correlationId, "tx-original-2", DecisionOutcome.BLOCK))).get();
        producer.flush();

        JsonNode result = pollForRecordWithKey("tx-original-2");
        assertThat(result.get("originalOutcome").asText()).isEqualTo("APPROVE");
        assertThat(result.get("newOutcome").asText()).isEqualTo("BLOCK");
        assertThat(result.get("drifted").asBoolean()).isTrue();
    }

    @Test
    void resultWithNoMatchingPendingCorrelationIsSilentlyDropped() throws Exception {
        String correlationId = "recon-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_RECONCILIATION_DECIDED, correlationId,
                decisionResult(correlationId, "tx-never-sampled", DecisionOutcome.APPROVE))).get();
        producer.flush();

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1));
            assertThat(records.records(Topics.TRANSACTIONS_RECONCILED)).isEmpty();
        });
    }

    private JsonNode pollForRecordWithKey(String key) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records.records(Topics.TRANSACTIONS_RECONCILED)) {
                if (key.equals(record.key())) {
                    return objectMapper.readTree(record.value());
                }
            }
        }
        throw new AssertionError("No reconciled record with key '" + key + "' arrived");
    }
}
