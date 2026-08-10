package com.ringwatch.decision.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.common.event.ReconciliationScoreResult;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.decision.repository.DecisionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** FR24: verifies the isolated re-decision never writes to the decisions table or transactions.decided. */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
        Topics.TRANSACTIONS_SCORED, Topics.TRANSACTIONS_DECIDED,
        Topics.TRANSACTIONS_RECONCILIATION_SCORED, Topics.TRANSACTIONS_RECONCILIATION_DECIDED})
class ReconciliationDecisionListenerIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private DecisionRepository decisionRepository;

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
                KafkaTestUtils.consumerProps("reconciliation-decision-test-consumer", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        embeddedKafkaBroker.consumeFromEmbeddedTopics(
                consumer, Topics.TRANSACTIONS_RECONCILIATION_DECIDED, Topics.TRANSACTIONS_DECIDED);
    }

    @AfterEach
    void tearDown() {
        producer.close();
        consumer.close();
    }

    private static ReconciliationScoreResult scoreResult(String correlationId, String originalTransactionId, String riskScore) {
        return new ReconciliationScoreResult(
                correlationId, originalTransactionId, "acct-sender", "acct-receiver",
                new BigDecimal("500.00"), "USD", "device-1", "10.0.0.1",
                Instant.now().truncatedTo(ChronoUnit.MILLIS), 3, new BigDecimal("50.00"),
                Set.of("device-1"), Set.of("10.0.0.1"), new BigDecimal(riskScore), "some explanation",
                ScoringMethod.AI);
    }

    @Test
    void reconciliationScoreIsDecidedAndPublishedWithoutTouchingTheRealDecisionsTableOrTopic() throws Exception {
        String correlationId = "recon-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_RECONCILIATION_SCORED, correlationId,
                scoreResult(correlationId, "tx-original-block", "0.90"))).get();
        producer.flush();

        JsonNode decided = pollForReconciliationDecided(correlationId);
        assertThat(decided.get("originalTransactionId").asText()).isEqualTo("tx-original-block");
        assertThat(decided.get("newOutcome").asText()).isEqualTo("BLOCK");

        assertThat(decisionRepository.findByTransactionId("tx-original-block")).isEmpty();
        assertThat(decisionRepository.findByTransactionId(correlationId)).isEmpty();

        ConsumerRecords<String, String> realTopicRecords = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
        assertThat(realTopicRecords.records(Topics.TRANSACTIONS_DECIDED)).isEmpty();
    }

    private JsonNode pollForReconciliationDecided(String correlationId) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records.records(Topics.TRANSACTIONS_RECONCILIATION_DECIDED)) {
                if (correlationId.equals(record.key())) {
                    try {
                        return objectMapper.readTree(record.value());
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                }
            }
        }
        throw new AssertionError("No reconciliation-decided record with key '" + correlationId + "' arrived");
    }
}
