package com.ringwatch.risk.service;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.risk.model.ScoringResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class RiskScoringService {

    private static final Logger log = LoggerFactory.getLogger(RiskScoringService.class);

    private final LlmRiskScorer llmRiskScorer;
    private final KafkaTemplate<String, ScoredTransactionEvent> kafkaTemplate;

    public RiskScoringService(LlmRiskScorer llmRiskScorer, KafkaTemplate<String, ScoredTransactionEvent> kafkaTemplate) {
        this.llmRiskScorer = llmRiskScorer;
        this.kafkaTemplate = kafkaTemplate;
    }

    public ScoredTransactionEvent scoreAndPublish(EnrichedTransactionEvent event) {
        ScoringResult result = llmRiskScorer.score(event);

        ScoredTransactionEvent scored = new ScoredTransactionEvent(
                event.transactionId(), event.senderAccountId(), event.receiverAccountId(),
                event.amount(), event.currency(), event.deviceId(), event.ipAddress(), event.timestamp(),
                event.recentTxnCount(), event.avgTxnAmount(), event.knownDevices(), event.knownIps(),
                result.score(), result.explanation(), result.method());

        kafkaTemplate.send(Topics.TRANSACTIONS_SCORED, event.senderAccountId(), scored)
                .whenComplete((sendResult, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish scored transaction '{}' to {}",
                                event.transactionId(), Topics.TRANSACTIONS_SCORED, ex);
                    }
                });

        return scored;
    }
}
