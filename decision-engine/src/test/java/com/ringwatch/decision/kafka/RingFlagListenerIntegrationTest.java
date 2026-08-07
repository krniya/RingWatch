package com.ringwatch.decision.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.common.event.FraudRingEvent;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.decision.ring.RingMembershipRegistry;
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
@EmbeddedKafka(
        partitions = 1,
        topics = {Topics.TRANSACTIONS_SCORED, Topics.TRANSACTIONS_RING_FLAGGED, Topics.TRANSACTIONS_DECIDED})
class RingFlagListenerIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private RingMembershipRegistry ringMembershipRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Producer<String, Object> producer;
    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producer = new DefaultKafkaProducerFactory<String, Object>(
                producerProps, new StringSerializer(), new JsonSerializer<>())
                .createProducer();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "ring-flag-test-consumer-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
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

    private static FraudRingEvent ringEvent(String accountId) {
        return new FraudRingEvent(
                "ring-" + UUID.randomUUID(), Set.of(accountId), "shared device", "ai explanation", Instant.now());
    }

    private static ScoredTransactionEvent scoredEvent(String transactionId, String sender, String receiver) {
        return new ScoredTransactionEvent(
                transactionId, sender, receiver, new BigDecimal("500.00"), "USD",
                "device-1", "10.0.0.1", Instant.now().truncatedTo(ChronoUnit.MILLIS),
                3, new BigDecimal("50.00"), Set.of("device-1"), Set.of("10.0.0.1"),
                new BigDecimal("0.10"), "some explanation", ScoringMethod.AI);
    }

    private void publishRingEventAndWaitUntilRegistered(String accountId) throws Exception {
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RING_FLAGGED, accountId, ringEvent(accountId))).get();
        producer.flush();
        await().atMost(Duration.ofSeconds(15))
                .until(() -> ringMembershipRegistry.membershipOf(accountId).isPresent());
    }

    @Test
    void senderRingMembershipEscalatesAnOtherwiseApprovedTransactionToFlag() throws Exception {
        String accountId = "acct-" + UUID.randomUUID();
        publishRingEventAndWaitUntilRegistered(accountId);

        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_SCORED, accountId, scoredEvent("tx-escalated", accountId, "receiver-1"))).get();
        producer.flush();

        JsonNode decided = objectMapper.readTree(pollForRecordWithKey(accountId).value());
        assertThat(decided.get("outcome").asText()).isEqualTo("FLAG");
        assertThat(decided.get("reason").asText()).contains("Escalated");
    }

    @Test
    void receiverRingMembershipAlsoEscalatesTheDecision() throws Exception {
        String sender = "acct-" + UUID.randomUUID();
        String receiver = "acct-" + UUID.randomUUID();
        publishRingEventAndWaitUntilRegistered(receiver);

        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_SCORED, sender, scoredEvent("tx-receiver-escalated", sender, receiver))).get();
        producer.flush();

        JsonNode decided = objectMapper.readTree(pollForRecordWithKey(sender).value());
        assertThat(decided.get("outcome").asText()).isEqualTo("FLAG");
    }

    @Test
    void accountsNotInAnyRingAreDecidedOnRiskScoreAlone() throws Exception {
        String accountId = "acct-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_SCORED, accountId, scoredEvent("tx-unrelated", accountId, "receiver-1"))).get();
        producer.flush();

        JsonNode decided = objectMapper.readTree(pollForRecordWithKey(accountId).value());
        assertThat(decided.get("outcome").asText()).isEqualTo("APPROVE");
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
