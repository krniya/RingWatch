package com.ringwatch.reconciliation.kafka;

import com.ringwatch.common.event.ReconciliationDecisionResult;
import com.ringwatch.common.event.ReconciliationResultEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.reconciliation.correlation.CorrelationStore;
import com.ringwatch.reconciliation.correlation.PendingReconciliation;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * FR24: the final leg of the reconciliation round-trip - correlates the isolated re-decision back
 * to the original decision, diffs the outcomes, and publishes the result for audit-service to
 * record. Both a producer (of the initial request, via {@link ReconciliationRequestProducer}) and
 * a consumer within this one service is the natural shape of a batch job orchestrating a
 * request/response round-trip over async topics.
 */
@Component
public class ReconciliationResultListener {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationResultListener.class);

    private final CorrelationStore correlationStore;
    private final KafkaTemplate<String, ReconciliationResultEvent> kafkaTemplate;

    public ReconciliationResultListener(
            CorrelationStore correlationStore, KafkaTemplate<String, ReconciliationResultEvent> kafkaTemplate) {
        this.correlationStore = correlationStore;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = Topics.TRANSACTIONS_RECONCILIATION_DECIDED,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.ReconciliationDecisionResult")
    public void onReconciliationDecided(ReconciliationDecisionResult event) {
        PendingReconciliation pending = correlationStore.remove(event.correlationId());
        if (pending == null) {
            log.warn("No pending reconciliation found for correlationId '{}' (transaction '{}') - "
                            + "likely a restart mid-run; the transaction will be re-sampled later.",
                    event.correlationId(), event.originalTransactionId());
            return;
        }

        boolean drifted = pending.originalOutcome() != event.newOutcome();
        ReconciliationResultEvent result = new ReconciliationResultEvent(
                pending.originalTransactionId(),
                pending.originalOutcome(),
                event.newOutcome(),
                pending.originalRiskScore(),
                event.newRiskScore(),
                pending.originalReason(),
                event.newReason(),
                drifted,
                Instant.now());

        kafkaTemplate.send(Topics.TRANSACTIONS_RECONCILED, pending.originalTransactionId(), result)
                .whenComplete((sendResult, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish reconciliation result for transaction '{}' to {}",
                                pending.originalTransactionId(), Topics.TRANSACTIONS_RECONCILED, ex);
                    }
                });
    }
}
