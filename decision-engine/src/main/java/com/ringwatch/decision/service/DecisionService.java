package com.ringwatch.decision.service;

import com.ringwatch.common.event.DecisionEvent;
import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.decision.model.Decision;
import com.ringwatch.decision.model.DecisionResult;
import com.ringwatch.decision.repository.DecisionRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Idempotent on {@code transactionId} the same way {@code IngestionService} is: if a decision
 * already exists (redelivery of the same scored event), the existing outcome is returned without
 * re-persisting or re-publishing, rather than risking a duplicate/conflicting decision.
 */
@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

    private final RuleBasedDecisionEngine decisionEngine;
    private final DecisionRepository decisionRepository;
    private final KafkaTemplate<String, DecisionEvent> kafkaTemplate;

    public DecisionService(
            RuleBasedDecisionEngine decisionEngine,
            DecisionRepository decisionRepository,
            KafkaTemplate<String, DecisionEvent> kafkaTemplate) {
        this.decisionEngine = decisionEngine;
        this.decisionRepository = decisionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public DecisionEvent decideAndPublish(ScoredTransactionEvent event) {
        Decision existing = decisionRepository.findByTransactionId(event.transactionId()).orElse(null);
        if (existing != null) {
            log.debug("Transaction '{}' was already decided ({}); skipping re-decision and republish.",
                    event.transactionId(), existing.getOutcome());
            return toEvent(event, existing.getOutcome(), existing.getReason(), existing.getCreatedAt());
        }

        DecisionResult result = decisionEngine.decide(event);
        Decision decision = new Decision(event.transactionId(), result.outcome(), result.reason());
        try {
            decisionRepository.save(decision);
        } catch (DataIntegrityViolationException e) {
            Decision raced = decisionRepository.findByTransactionId(event.transactionId()).orElseThrow(() -> e);
            log.debug("Transaction '{}' was decided concurrently ({}); skipping republish.",
                    event.transactionId(), raced.getOutcome());
            return toEvent(event, raced.getOutcome(), raced.getReason(), raced.getCreatedAt());
        }

        DecisionEvent decisionEvent = toEvent(event, result.outcome(), result.reason(), Instant.now());
        kafkaTemplate.send(Topics.TRANSACTIONS_DECIDED, event.senderAccountId(), decisionEvent)
                .whenComplete((sendResult, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish decision for transaction '{}' to {}",
                                event.transactionId(), Topics.TRANSACTIONS_DECIDED, ex);
                    }
                });
        return decisionEvent;
    }

    private static DecisionEvent toEvent(
            ScoredTransactionEvent event, DecisionOutcome outcome, String reason, Instant decidedAt) {
        return new DecisionEvent(
                event.transactionId(), event.senderAccountId(), event.receiverAccountId(),
                event.amount(), event.currency(), event.deviceId(), event.ipAddress(), event.timestamp(),
                event.recentTxnCount(), event.avgTxnAmount(), event.knownDevices(), event.knownIps(),
                event.riskScore(), event.explanation(), event.scoringMethod(),
                outcome, reason, decidedAt);
    }
}
