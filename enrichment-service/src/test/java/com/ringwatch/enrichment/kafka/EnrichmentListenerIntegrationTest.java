package com.ringwatch.enrichment.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.common.event.TransactionRawEvent;
import com.ringwatch.common.kafka.Topics;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
@EmbeddedKafka(partitions = 1, topics = {Topics.TRANSACTIONS_RAW, Topics.TRANSACTIONS_ENRICHED})
class EnrichmentListenerIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

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
                KafkaTestUtils.consumerProps("enrichment-test-consumer", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, Topics.TRANSACTIONS_ENRICHED);
    }

    @AfterEach
    void tearDown() {
        producer.close();
        consumer.close();
    }

    @Test
    void secondTransactionFromSameAccountReflectsFirstAsHistory() throws Exception {
        String accountId = "acct-" + UUID.randomUUID();

        TransactionRawEvent first = new TransactionRawEvent(
                "tx-1", accountId, "receiver-1", new BigDecimal("100.00"), "USD",
                "device-1", "10.0.0.1", Instant.now().truncatedTo(ChronoUnit.MILLIS));
        TransactionRawEvent second = new TransactionRawEvent(
                "tx-2", accountId, "receiver-2", new BigDecimal("50.00"), "USD",
                "device-2", "10.0.0.2", Instant.now().truncatedTo(ChronoUnit.MILLIS));

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RAW, accountId, first)).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RAW, accountId, second)).get();
        producer.flush();

        List<ConsumerRecord<String, String>> received = pollForRecordsWithKey(accountId, 2);

        JsonNode firstEnriched = objectMapper.readTree(received.get(0).value());
        assertThat(firstEnriched.get("transactionId").asText()).isEqualTo("tx-1");
        assertThat(firstEnriched.get("recentTxnCount").asInt()).isZero();

        JsonNode secondEnriched = objectMapper.readTree(received.get(1).value());
        assertThat(secondEnriched.get("transactionId").asText()).isEqualTo("tx-2");
        assertThat(secondEnriched.get("recentTxnCount").asInt()).isEqualTo(1);
        assertThat(secondEnriched.get("avgTxnAmount").decimalValue()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(secondEnriched.get("knownDevices").toString()).contains("device-1");
    }

    @Test
    void poisonPillMessageIsSkippedWithoutBlockingSubsequentValidMessages() throws Exception {
        String accountId = "acct-" + UUID.randomUUID();

        TransactionRawEvent valid = new TransactionRawEvent(
                "tx-after-poison-pill", accountId, "receiver-1", new BigDecimal("25.00"), "USD",
                "device-1", "10.0.0.1", Instant.now().truncatedTo(ChronoUnit.MILLIS));

        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RAW, accountId, "not-a-transaction")).get();
        producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RAW, accountId, valid)).get();
        producer.flush();

        List<ConsumerRecord<String, String>> received = pollForRecordsWithKey(accountId, 1);

        JsonNode enriched = objectMapper.readTree(received.get(0).value());
        assertThat(enriched.get("transactionId").asText()).isEqualTo("tx-after-poison-pill");
    }

    /**
     * The embedded topic is shared across every test method in this class (and
     * {@code consumeFromAnEmbeddedTopic} always seeks to the beginning), so earlier tests' records
     * are still present when a later test polls. Filtering by this test's own randomly generated
     * account key keeps each test's assertions independent of execution order and leftover data.
     */
    private List<ConsumerRecord<String, String>> pollForRecordsWithKey(String key, int expectedCount) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 15_000;
        while (matching.size() < expectedCount && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
            records.records(Topics.TRANSACTIONS_ENRICHED).forEach(record -> {
                if (key.equals(record.key())) {
                    matching.add(record);
                }
            });
        }
        assertThat(matching).hasSize(expectedCount);
        return matching;
    }
}
