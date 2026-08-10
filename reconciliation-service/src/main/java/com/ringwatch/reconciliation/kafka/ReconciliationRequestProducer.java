package com.ringwatch.reconciliation.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.event.ReconciliationScoreRequest;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.reconciliation.audit.dto.AuditLogEntryDto;
import com.ringwatch.reconciliation.correlation.CorrelationStore;
import com.ringwatch.reconciliation.correlation.PendingReconciliation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * FR24: turns a sampled DECIDED audit entry (a {@code DecisionEvent}-shaped JSON payload) into a
 * {@link ReconciliationScoreRequest}, records the original outcome for later diffing, and
 * publishes the request.
 */
@Component
public class ReconciliationRequestProducer {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationRequestProducer.class);

    private final CorrelationStore correlationStore;
    private final KafkaTemplate<String, ReconciliationScoreRequest> kafkaTemplate;

    public ReconciliationRequestProducer(
            CorrelationStore correlationStore, KafkaTemplate<String, ReconciliationScoreRequest> kafkaTemplate) {
        this.correlationStore = correlationStore;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void requestReconciliation(AuditLogEntryDto decidedEntry) {
        JsonNode payload = decidedEntry.payload();
        String correlationId = UUID.randomUUID().toString();

        correlationStore.put(correlationId, new PendingReconciliation(
                decidedEntry.transactionId(),
                DecisionOutcome.valueOf(payload.get("outcome").asText()),
                new BigDecimal(payload.get("riskScore").asText()),
                payload.get("reason").asText(),
                Instant.now()));

        ReconciliationScoreRequest request = new ReconciliationScoreRequest(
                correlationId,
                decidedEntry.transactionId(),
                payload.get("senderAccountId").asText(),
                payload.get("receiverAccountId").asText(),
                new BigDecimal(payload.get("amount").asText()),
                payload.get("currency").asText(),
                payload.get("deviceId").asText(),
                payload.get("ipAddress").asText(),
                Instant.parse(payload.get("timestamp").asText()),
                payload.get("recentTxnCount").asInt(),
                new BigDecimal(payload.get("avgTxnAmount").asText()),
                toStringSet(payload.get("knownDevices")),
                toStringSet(payload.get("knownIps")));

        kafkaTemplate.send(Topics.TRANSACTIONS_RECONCILIATION_SCORING_REQUESTED, correlationId, request)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish reconciliation request for transaction '{}' to {}",
                                decidedEntry.transactionId(), Topics.TRANSACTIONS_RECONCILIATION_SCORING_REQUESTED, ex);
                    }
                });
    }

    private static Set<String> toStringSet(JsonNode arrayNode) {
        Set<String> values = new HashSet<>();
        if (arrayNode != null) {
            arrayNode.forEach(node -> values.add(node.asText()));
        }
        return values;
    }
}
