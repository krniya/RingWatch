package com.ringwatch.notification.service;

import com.ringwatch.common.event.AlertEvent;
import com.ringwatch.common.event.AlertType;
import com.ringwatch.common.event.DecisionEvent;
import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.event.FraudRingEvent;
import com.ringwatch.common.kafka.Topics;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * FR29: alerts on every FLAG/BLOCK decision and every newly detected fraud ring. {@code
 * notifyOnRing} deliberately applies no additional de-dup filtering of its own, but this is only a
 * partial safety net, not a guarantee of one alert per real-world ring: fraud-ring-detection-
 * service's {@code AccountClusterGraph} does only publish to {@code transactions.ring-flagged} on
 * genuine cluster growth, but that service's other detection signal, {@code TransactionGraph}'s
 * BFS cycle detection, has no equivalent guard - a later, ordinary transaction that closes an
 * already-detected cycle again re-publishes a brand-new {@code FraudRingEvent} (fresh {@code
 * ringId}) for what is semantically the same ring (see {@code TransactionGraph}'s javadoc/tests -
 * a documented, already-accepted characteristic of that already-shipped service, not something to
 * fix here). Separately, this service has no persistence layer, so it also has no protection
 * against a raw Kafka *redelivery* of the same message (e.g., a consumer restart or rebalance
 * before offset commit). Both gaps mean analysts can occasionally see a duplicate alert for
 * either signal type; accepted per FR32's best-effort framing rather than solved with added
 * state, since that's a much smaller problem than the pipeline-blocking one FR32 actually guards
 * against.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final EmailNotifier emailNotifier;
    private final KafkaTemplate<String, AlertEvent> kafkaTemplate;

    public NotificationService(EmailNotifier emailNotifier, KafkaTemplate<String, AlertEvent> kafkaTemplate) {
        this.emailNotifier = emailNotifier;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void notifyOnDecision(DecisionEvent event) {
        if (event.outcome() == DecisionOutcome.APPROVE) {
            return;
        }
        AlertType alertType = event.outcome() == DecisionOutcome.BLOCK
                ? AlertType.TRANSACTION_BLOCKED
                : AlertType.TRANSACTION_FLAGGED;
        String subject = "RingWatch Alert: Transaction %s %s".formatted(event.transactionId(), event.outcome());
        String body = "Transaction %s (%s -> %s, %s %s) was %s.\nReason: %s".formatted(
                event.transactionId(), event.senderAccountId(), event.receiverAccountId(),
                event.amount(), event.currency(), event.outcome(), event.reason());

        publish(alertType, event.transactionId(), null, subject, body);
    }

    public void notifyOnRing(FraudRingEvent event) {
        String subject = "RingWatch Alert: New Fraud Ring Detected (%s)".formatted(event.ringId());
        String body = "Ring %s: %s\nMembers: %s\n%s".formatted(
                event.ringId(), event.sharedAttributes(),
                String.join(", ", event.memberAccountIds()), event.aiExplanation());

        publish(AlertType.RING_DETECTED, null, event.ringId(), subject, body);
    }

    private void publish(AlertType alertType, String transactionId, String ringId, String subject, String body) {
        emailNotifier.send(subject, body);

        AlertEvent alertEvent = new AlertEvent(
                UUID.randomUUID().toString(), alertType, transactionId, ringId, body, Instant.now());
        String key = transactionId != null ? transactionId : ringId;
        kafkaTemplate.send(Topics.NOTIFICATIONS_ALERTS, key, alertEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish alert '{}' to {}",
                                alertEvent.alertId(), Topics.NOTIFICATIONS_ALERTS, ex);
                    }
                });
    }
}
