package com.ringwatch.risk.kafka;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.event.ReconciliationScoreRequest;
import com.ringwatch.common.event.ReconciliationScoreResult;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.risk.model.ScoringResult;
import com.ringwatch.risk.service.LlmRiskScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * FR24: re-scores a sampled past transaction in isolation. Deliberately calls {@link
 * LlmRiskScorer} directly instead of {@link com.ringwatch.risk.service.RiskScoringService}, which
 * publishes to the real {@code transactions.scored} - reconciliation must never touch that topic
 * or anything downstream of it.
 */
@Component
public class ReconciliationScoringListener {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScoringListener.class);

    private final LlmRiskScorer llmRiskScorer;
    private final KafkaTemplate<String, ReconciliationScoreResult> kafkaTemplate;

    public ReconciliationScoringListener(
            LlmRiskScorer llmRiskScorer, KafkaTemplate<String, ReconciliationScoreResult> kafkaTemplate) {
        this.llmRiskScorer = llmRiskScorer;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = Topics.TRANSACTIONS_RECONCILIATION_SCORING_REQUESTED,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.ReconciliationScoreRequest")
    public void onReconciliationScoringRequested(ReconciliationScoreRequest request) {
        // correlationId stands in for transactionId here - confirmed safe: LlmRiskScorer's prompt
        // never reads transactionId, only sender/receiver/device/IP/amount/history fields.
        EnrichedTransactionEvent syntheticEvent = new EnrichedTransactionEvent(
                request.correlationId(), request.senderAccountId(), request.receiverAccountId(),
                request.amount(), request.currency(), request.deviceId(), request.ipAddress(),
                request.timestamp(), request.recentTxnCount(), request.avgTxnAmount(),
                request.knownDevices(), request.knownIps());

        ScoringResult result = llmRiskScorer.score(syntheticEvent);

        ReconciliationScoreResult scored = new ReconciliationScoreResult(
                request.correlationId(), request.originalTransactionId(),
                request.senderAccountId(), request.receiverAccountId(), request.amount(), request.currency(),
                request.deviceId(), request.ipAddress(), request.timestamp(), request.recentTxnCount(),
                request.avgTxnAmount(), request.knownDevices(), request.knownIps(),
                result.score(), result.explanation(), result.method());

        kafkaTemplate.send(Topics.TRANSACTIONS_RECONCILIATION_SCORED, request.correlationId(), scored)
                .whenComplete((sendResult, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish reconciliation score for transaction '{}' to {}",
                                request.originalTransactionId(), Topics.TRANSACTIONS_RECONCILIATION_SCORED, ex);
                    }
                });
    }
}
