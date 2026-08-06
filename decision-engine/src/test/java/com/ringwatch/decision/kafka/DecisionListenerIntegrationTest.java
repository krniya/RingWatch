package com.ringwatch.decision.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.decision.repository.DecisionRepository;
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

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {Topics.TRANSACTIONS_SCORED, Topics.TRANSACTIONS_DECIDED})
class DecisionListenerIntegrationTest {

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
                KafkaTestUtils.consumerProps("decision-engine-test-consumer", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, Topics.TRANSACTIONS_DECIDED);
    }

    @AfterEach
    void tearDown() {
        producer.close();
        consumer.close();
    }

    private static ScoredTransactionEvent event(String transactionId, String accountId, String riskScore) {
        return new ScoredTransactionEvent(
                transactionId, accountId, "receiver-1", new BigDecimal("500.00"), "USD",
                "device-1", "10.0.0.1", Instant.now().truncatedTo(ChronoUnit.MILLIS),
                3, new BigDecimal("50.00"), Set.of("device-1"), Set.of("10.0.0.1"),
                new BigDecimal(riskScore), "some explanation", ScoringMethod.AI);
    }

    @Test
    void lowRiskScoreIsApprovedAndPublished() throws Exception {
        String accountId = "acct-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_SCORED, accountId, event("tx-approve", accountId, "0.10")))
                .get();
        producer.flush();

        JsonNode decided = objectMapper.readTree(pollForRecordWithKey(accountId).value());
        assertThat(decided.get("transactionId").asText()).isEqualTo("tx-approve");
        assertThat(decided.get("outcome").asText()).isEqualTo("APPROVE");
        assertThat(decisionRepository.findByTransactionId("tx-approve")).isPresent();
    }

    @Test
    void highRiskScoreIsBlockedAndPublished() throws Exception {
        String accountId = "acct-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_SCORED, accountId, event("tx-block", accountId, "0.90")))
                .get();
        producer.flush();

        JsonNode decided = objectMapper.readTree(pollForRecordWithKey(accountId).value());
        assertThat(decided.get("transactionId").asText()).isEqualTo("tx-block");
        assertThat(decided.get("outcome").asText()).isEqualTo("BLOCK");
        assertThat(decisionRepository.findByTransactionId("tx-block").orElseThrow().getOutcome().name())
                .isEqualTo("BLOCK");
    }

    @Test
    void poisonPillMessageIsSkippedWithoutBlockingSubsequentValidMessages() throws Exception {
        String accountId = "acct-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_SCORED, accountId, "not-a-transaction")).get();
        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_SCORED, accountId, event("tx-after-poison-pill", accountId, "0.10"))).get();
        producer.flush();

        JsonNode decided = objectMapper.readTree(pollForRecordWithKey(accountId).value());
        assertThat(decided.get("transactionId").asText()).isEqualTo("tx-after-poison-pill");
    }

    @Test
    void redeliveredTransactionIsNotRepublishedAsADuplicateDecision() throws Exception {
        String accountId = "acct-" + UUID.randomUUID();
        ScoredTransactionEvent event = event("tx-redelivered", accountId, "0.20");

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_SCORED, accountId, event)).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_SCORED, accountId, event)).get();
        producer.flush();

        // First delivery publishes; wait for it, then confirm no second decision record follows.
        pollForRecordWithKey(accountId);
        ConsumerRecords<String, String> extra = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(3));
        List<ConsumerRecord<String, String>> extraDecided = new ArrayList<>();
        extra.records(Topics.TRANSACTIONS_DECIDED).forEach(extraDecided::add);
        assertThat(extraDecided).isEmpty();
    }

    private ConsumerRecord<String, String> pollForRecordWithKey(String key) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 15_000;
        while (matching.isEmpty() && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
            records.records(Topics.TRANSACTIONS_DECIDED).forEach(record -> {
                if (key.equals(record.key())) {
                    matching.add(record);
                }
            });
        }
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }
}
