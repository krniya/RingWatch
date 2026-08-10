package com.ringwatch.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Published to {@code Topics.TRANSACTIONS_RECONCILIATION_SCORED} (FR24). Carries the full
 * {@link ScoredTransactionEvent} field set, not just the fresh score - decision-engine's
 * reconciliation listener has no other source for the enrichment inputs
 * {@code RuleBasedDecisionEngine.decide} needs.
 */
public record ReconciliationScoreResult(
        String correlationId,
        String originalTransactionId,
        String senderAccountId,
        String receiverAccountId,
        BigDecimal amount,
        String currency,
        String deviceId,
        String ipAddress,
        Instant timestamp,
        int recentTxnCount,
        BigDecimal avgTxnAmount,
        Set<String> knownDevices,
        Set<String> knownIps,
        BigDecimal riskScore,
        String explanation,
        ScoringMethod scoringMethod
) {
}
