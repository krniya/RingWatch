package com.ringwatch.decision.kafka;

import com.ringwatch.common.event.ReconciliationDecisionResult;
import com.ringwatch.common.event.ReconciliationScoreResult;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.decision.model.DecisionResult;
import com.ringwatch.decision.service.RuleBasedDecisionEngine;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * FR24: re-decides a sampled past transaction in isolation. Deliberately calls {@link
 * RuleBasedDecisionEngine} directly instead of {@link com.ringwatch.decision.service.DecisionService},
 * which persists to the {@code decisions} table and publishes to the real {@code
 * transactions.decided} - reconciliation must never touch either. Also deliberately bypasses
 * {@link com.ringwatch.decision.priority.DecisionPriorityQueue}/{@link DecisionWorker}: that queue
 * exists to prioritize live traffic under load (FR14), and reconciliation has no latency SLA to
 * justify competing with real transactions for queue capacity.
 */
@Component
public class ReconciliationDecisionListener {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationDecisionListener.class);

    private final RuleBasedDecisionEngine decisionEngine;
    private final KafkaTemplate<String, ReconciliationDecisionResult> kafkaTemplate;

    public ReconciliationDecisionListener(
            RuleBasedDecisionEngine decisionEngine, KafkaTemplate<String, ReconciliationDecisionResult> kafkaTemplate) {
        this.decisionEngine = decisionEngine;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = Topics.TRANSACTIONS_RECONCILIATION_SCORED,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.ReconciliationScoreResult")
    public void onReconciliationScored(ReconciliationScoreResult event) {
        ScoredTransactionEvent syntheticEvent = new ScoredTransactionEvent(
                event.correlationId(), event.senderAccountId(), event.receiverAccountId(),
                event.amount(), event.currency(), event.deviceId(), event.ipAddress(), event.timestamp(),
                event.recentTxnCount(), event.avgTxnAmount(), event.knownDevices(), event.knownIps(),
                event.riskScore(), event.explanation(), event.scoringMethod());

        DecisionResult result = decisionEngine.decide(syntheticEvent);

        ReconciliationDecisionResult decided = new ReconciliationDecisionResult(
                event.correlationId(), event.originalTransactionId(),
                result.outcome(), result.reason(), event.riskScore(), Instant.now());

        kafkaTemplate.send(Topics.TRANSACTIONS_RECONCILIATION_DECIDED, event.correlationId(), decided)
                .whenComplete((sendResult, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish reconciliation decision for transaction '{}' to {}",
                                event.originalTransactionId(), Topics.TRANSACTIONS_RECONCILIATION_DECIDED, ex);
                    }
                });
    }
}
