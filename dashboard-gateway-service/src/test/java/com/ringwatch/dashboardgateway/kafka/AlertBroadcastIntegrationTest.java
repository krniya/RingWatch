package com.ringwatch.dashboardgateway.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ringwatch.common.event.AlertEvent;
import com.ringwatch.common.event.AlertType;
import com.ringwatch.common.kafka.Topics;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * FR31's genuinely new test pattern for this repo: a real WebSocket client connecting to a real
 * embedded server, proving the full round trip (Kafka publish -&gt; broadcast -&gt; client receives)
 * end to end, the same way every Kafka-consumer integration test elsewhere proves its own
 * publish-and-poll round trip.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = {Topics.NOTIFICATIONS_ALERTS})
class AlertBroadcastIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${ringwatch.jwt.secret}")
    private String jwtSecret;

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

    private String validToken() {
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", "alice")
                .claim("role", "ANALYST")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(signingKey)
                .compact();
    }

    @Test
    void alertPublishedToKafkaIsBroadcastToTheConnectedClient() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client
                .execute(new CapturingHandler(received), "ws://localhost:" + port + "/ws/alerts?token=" + validToken())
                .get(10, TimeUnit.SECONDS);

        try {
            AlertEvent event = new AlertEvent(
                    "alert-1", AlertType.TRANSACTION_BLOCKED, "tx-1", null,
                    "Transaction tx-1 blocked", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
            producer.send(new ProducerRecord<>(Topics.NOTIFICATIONS_ALERTS, "tx-1", event)).get();
            producer.flush();

            String message = received.poll(15, TimeUnit.SECONDS);
            assertThat(message).isNotNull();
            assertThat(message).contains("\"alertId\":\"alert-1\"").contains("TRANSACTION_BLOCKED");
        } finally {
            session.close();
        }
    }

    @Test
    void connectionWithoutAValidTokenIsRejectedDuringTheHandshake() {
        StandardWebSocketClient client = new StandardWebSocketClient();
        CompletableFuture<WebSocketSession> future =
                client.execute(new TextWebSocketHandler() { }, "ws://localhost:" + port + "/ws/alerts");

        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    }

    private static final class CapturingHandler extends TextWebSocketHandler {
        private final BlockingQueue<String> received;

        private CapturingHandler(BlockingQueue<String> received) {
            this.received = received;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            received.offer(message.getPayload());
        }
    }
}
