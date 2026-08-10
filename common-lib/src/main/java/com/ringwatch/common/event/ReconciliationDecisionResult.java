package com.ringwatch.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published to {@code Topics.TRANSACTIONS_RECONCILIATION_DECIDED} (FR24): decision-engine's fresh,
 * isolated re-decision for a sampled past transaction - never written to the {@code decisions}
 * table, never published to the real {@code transactions.decided}.
 */
public record ReconciliationDecisionResult(
        String correlationId,
        String originalTransactionId,
        DecisionOutcome newOutcome,
        String newReason,
        BigDecimal newRiskScore,
        Instant checkedAt
) {
}
