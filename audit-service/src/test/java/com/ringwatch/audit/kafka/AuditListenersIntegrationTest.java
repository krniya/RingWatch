package com.ringwatch.audit.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ringwatch.audit.model.AuditEventType;
import com.ringwatch.audit.repository.AuditLogRepository;
import com.ringwatch.common.event.DecisionEvent;
import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.event.DecisionOverriddenEvent;
import com.ringwatch.common.event.ReconciliationResultEvent;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.common.event.TransactionRawEvent;
import com.ringwatch.common.kafka.Topics;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {Topics.TRANSACTIONS_RAW, Topics.TRANSACTIONS_SCORED, Topics.TRANSACTIONS_DECIDED,
                Topics.TRANSACTIONS_OVERRIDDEN, Topics.TRANSACTIONS_RECONCILED})
class AuditListenersIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Producer<String, Object> producer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producer = new DefaultKafkaProducerFactory<String, Object>(
                producerProps, new StringSerializer(), new JsonSerializer<>())
                .createProducer();
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    private static TransactionRawEvent rawEvent(String transactionId) {
        return new TransactionRawEvent(
                transactionId, "sender-1", "receiver-1", new BigDecimal("100.00"), "USD",
                "device-1", "10.0.0.1", Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    private static ScoredTransactionEvent scoredEvent(String transactionId) {
        return new ScoredTransactionEvent(
                transactionId, "sender-1", "receiver-1", new BigDecimal("100.00"), "USD",
                "device-1", "10.0.0.1", Instant.now().truncatedTo(ChronoUnit.MILLIS),
                1, BigDecimal.TEN, Set.of("device-1"), Set.of("10.0.0.1"),
                new BigDecimal("0.20"), "some explanation", ScoringMethod.AI);
    }

    private static DecisionEvent decidedEvent(String transactionId) {
        return new DecisionEvent(
                transactionId, "sender-1", "receiver-1", new BigDecimal("100.00"), "USD",
                "device-1", "10.0.0.1", Instant.now().truncatedTo(ChronoUnit.MILLIS),
                1, BigDecimal.TEN, Set.of("device-1"), Set.of("10.0.0.1"),
                new BigDecimal("0.20"), "some explanation", ScoringMethod.AI,
                DecisionOutcome.APPROVE, "some reason", Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    private static DecisionOverriddenEvent overriddenEvent(String transactionId) {
        return new DecisionOverriddenEvent(
                transactionId, DecisionOutcome.APPROVE, "confirmed legitimate", "alice",
                "confirmed legitimate", Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    private static ReconciliationResultEvent reconciledEvent(String transactionId, boolean drifted) {
        return new ReconciliationResultEvent(
                transactionId, DecisionOutcome.APPROVE, drifted ? DecisionOutcome.FLAG : DecisionOutcome.APPROVE,
                new BigDecimal("0.20"), drifted ? new BigDecimal("0.50") : new BigDecimal("0.20"),
                "original reason", "new reason", drifted, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    private void awaitEntry(String transactionId, AuditEventType eventType) {
        await().atMost(Duration.ofSeconds(15)).until(() ->
                auditLogRepository.findByTransactionIdOrderByRecordedAtAsc(transactionId).stream()
                        .anyMatch(entry -> entry.getEventType() == eventType));
    }

    @Test
    void rawTransactionIsRecordedAsCreated() throws Exception {
        String transactionId = "tx-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RAW, transactionId, rawEvent(transactionId))).get();
        producer.flush();

        awaitEntry(transactionId, AuditEventType.CREATED);
        var entry = auditLogRepository.findByTransactionIdOrderByRecordedAtAsc(transactionId).get(0);
        assertThat(entry.getPayload()).contains(transactionId);
    }

    @Test
    void scoredTransactionIsRecordedAsScored() throws Exception {
        String transactionId = "tx-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_SCORED, transactionId, scoredEvent(transactionId))).get();
        producer.flush();

        awaitEntry(transactionId, AuditEventType.SCORED);
        var entry = auditLogRepository.findByTransactionIdOrderByRecordedAtAsc(transactionId).get(0);
        assertThat(entry.getPayload()).contains("riskScore");
    }

    @Test
    void decidedTransactionIsRecordedAsDecided() throws Exception {
        String transactionId = "tx-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_DECIDED, transactionId, decidedEvent(transactionId))).get();
        producer.flush();

        awaitEntry(transactionId, AuditEventType.DECIDED);
        var entry = auditLogRepository.findByTransactionIdOrderByRecordedAtAsc(transactionId).get(0);
        assertThat(entry.getPayload()).contains("APPROVE");
    }

    @Test
    void overriddenTransactionIsRecordedAsOverriddenWithTheAnalystIdentity() throws Exception {
        String transactionId = "tx-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_OVERRIDDEN, transactionId, overriddenEvent(transactionId)))
                .get();
        producer.flush();

        awaitEntry(transactionId, AuditEventType.OVERRIDDEN);
        var entry = auditLogRepository.findByTransactionIdOrderByRecordedAtAsc(transactionId).get(0);
        assertThat(entry.getUserId()).isEqualTo("alice");
        assertThat(entry.getPayload()).contains("confirmed legitimate");
    }

    @Test
    void reconciledTransactionIsRecordedAsReconciledAgainstTheOriginalTransactionId() throws Exception {
        String transactionId = "tx-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RECONCILED, transactionId, reconciledEvent(transactionId, true)))
                .get();
        producer.flush();

        awaitEntry(transactionId, AuditEventType.RECONCILED);
        var entry = auditLogRepository.findByTransactionIdOrderByRecordedAtAsc(transactionId).get(0);
        assertThat(entry.getPayload()).contains("\"drifted\":true");
    }

    @Test
    void aPoisonPillOnOneTopicDoesNotBlockSubsequentValidMessagesOnAnyTopic() throws Exception {
        String badId = "tx-" + UUID.randomUUID();
        String goodRawId = "tx-" + UUID.randomUUID();
        String goodScoredId = "tx-" + UUID.randomUUID();
        String goodDecidedId = "tx-" + UUID.randomUUID();
        String goodOverriddenId = "tx-" + UUID.randomUUID();
        String goodReconciledId = "tx-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RAW, badId, "not-a-transaction")).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_SCORED, badId, "not-a-transaction")).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_DECIDED, badId, "not-a-transaction")).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_OVERRIDDEN, badId, "not-a-transaction")).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RECONCILED, badId, "not-a-transaction")).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RAW, goodRawId, rawEvent(goodRawId))).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_SCORED, goodScoredId, scoredEvent(goodScoredId))).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_DECIDED, goodDecidedId, decidedEvent(goodDecidedId))).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_OVERRIDDEN, goodOverriddenId, overriddenEvent(goodOverriddenId)))
                .get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RECONCILED, goodReconciledId, reconciledEvent(goodReconciledId, false)))
                .get();
        producer.flush();

        awaitEntry(goodRawId, AuditEventType.CREATED);
        awaitEntry(goodScoredId, AuditEventType.SCORED);
        awaitEntry(goodDecidedId, AuditEventType.DECIDED);
        awaitEntry(goodOverriddenId, AuditEventType.OVERRIDDEN);
        awaitEntry(goodReconciledId, AuditEventType.RECONCILED);
    }
}
