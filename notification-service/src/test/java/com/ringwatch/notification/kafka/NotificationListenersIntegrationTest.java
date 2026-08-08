package com.ringwatch.notification.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.ringwatch.common.event.DecisionEvent;
import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.event.FraudRingEvent;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.common.kafka.Topics;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
        topics = {Topics.TRANSACTIONS_DECIDED, Topics.TRANSACTIONS_RING_FLAGGED, Topics.NOTIFICATIONS_ALERTS})
class NotificationListenersIntegrationTest {

    @RegisterExtension
    GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Producer<String, Object> producer;
    private Consumer<String, String> alertsConsumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producer = new DefaultKafkaProducerFactory<String, Object>(
                producerProps, new StringSerializer(), new JsonSerializer<>())
                .createProducer();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "notification-test-consumer-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        alertsConsumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(alertsConsumer, Topics.NOTIFICATIONS_ALERTS);
    }

    @AfterEach
    void tearDown() {
        producer.close();
        alertsConsumer.close();
    }

    private static DecisionEvent decisionEvent(String transactionId, DecisionOutcome outcome) {
        return new DecisionEvent(
                transactionId, "sender-1", "receiver-1", new BigDecimal("500.00"), "USD",
                "device-1", "10.0.0.1", Instant.now().truncatedTo(ChronoUnit.MILLIS),
                3, new BigDecimal("50.00"), Set.of("device-1"), Set.of("10.0.0.1"),
                new BigDecimal("0.80"), "some explanation", ScoringMethod.AI,
                outcome, "some reason", Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    private static FraudRingEvent ringEvent(String ringId) {
        return new FraudRingEvent(ringId, Set.of("A", "B"), "shared device", "ai explanation", Instant.now());
    }

    @Test
    void flaggedDecisionProducesAnEmailAndAnAlertEvent() throws Exception {
        String transactionId = "tx-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_DECIDED, transactionId, decisionEvent(transactionId, DecisionOutcome.FLAG))).get();
        producer.flush();

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage email = greenMail.getReceivedMessages()[0];
        assertThat(email.getSubject()).contains(transactionId).contains("FLAG");

        assertThat(pollForRecordWithKey(transactionId)).isNotNull();
    }

    @Test
    void approvedDecisionProducesNoEmailAndNoAlertEvent() throws Exception {
        String transactionId = "tx-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_DECIDED, transactionId, decisionEvent(transactionId, DecisionOutcome.APPROVE))).get();
        producer.flush();

        // Can't assert the topic is globally empty - this class's other tests share the same
        // cached broker/topic, and this test's fresh (randomUUID) consumer group reads the whole
        // topic history from "earliest", including their leftover records. Filter by this test's
        // own unique transactionId instead.
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(alertsConsumer, Duration.ofSeconds(3));
        boolean alertForThisTransaction = false;
        for (var record : records.records(Topics.NOTIFICATIONS_ALERTS)) {
            if (transactionId.equals(record.key())) {
                alertForThisTransaction = true;
            }
        }
        assertThat(alertForThisTransaction).isFalse();
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void ringDetectionProducesAnEmailAndAnAlertEvent() throws Exception {
        String ringId = "ring-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RING_FLAGGED, ringId, ringEvent(ringId))).get();
        producer.flush();

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage email = greenMail.getReceivedMessages()[0];
        assertThat(email.getSubject()).contains(ringId);

        assertThat(pollForRecordWithKey(ringId)).isNotNull();
    }

    @Test
    void poisonPillMessageOnOneTopicDoesNotBlockSubsequentValidMessagesOnAnyTopic() throws Exception {
        String badId = "tx-" + UUID.randomUUID();
        String goodDecisionId = "tx-" + UUID.randomUUID();
        String goodRingId = "ring-" + UUID.randomUUID();

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_DECIDED, badId, "not-a-decision")).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RING_FLAGGED, badId, "not-a-ring")).get();
        producer.send(new ProducerRecord<>(
                Topics.TRANSACTIONS_DECIDED, goodDecisionId, decisionEvent(goodDecisionId, DecisionOutcome.BLOCK))).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RING_FLAGGED, goodRingId, ringEvent(goodRingId))).get();
        producer.flush();

        // Poll for both keys in one shared loop, not two sequential pollForRecordWithKey() calls -
        // both alerts can land in the same poll() batch, and a single-target poll would discard
        // (not re-deliver) the other key's record once fetched, causing a false failure.
        Set<String> seenKeys = new HashSet<>();
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline
                && !(seenKeys.contains(goodDecisionId) && seenKeys.contains(goodRingId))) {
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(alertsConsumer, Duration.ofSeconds(2));
            records.records(Topics.NOTIFICATIONS_ALERTS).forEach(record -> seenKeys.add(record.key()));
        }

        assertThat(seenKeys).contains(goodDecisionId, goodRingId);
    }

    private Object pollForRecordWithKey(String key) {
        // Generous deadline: unlike other listeners in this repo, notifyOnDecision/notifyOnRing
        // synchronously attempt an SMTP send (with Resilience4j retries) before publishing to
        // Kafka, so per-message latency here is real, not just Kafka's own poll interval.
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(alertsConsumer, Duration.ofSeconds(2));
            for (var record : records.records(Topics.NOTIFICATIONS_ALERTS)) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No alert record found for key " + key);
    }
}
