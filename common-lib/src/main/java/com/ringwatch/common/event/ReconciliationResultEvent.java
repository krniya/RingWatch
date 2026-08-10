package com.ringwatch.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published to {@code Topics.TRANSACTIONS_RECONCILED} (FR24) once reconciliation-service
 * correlates a fresh {@link ReconciliationDecisionResult} back to the original sampled decision.
 * Consumed by audit-service to write a {@code RECONCILED} entry against {@code
 * originalTransactionId}. {@code drifted} is {@code originalOutcome != newOutcome} - the single
 * signal FR24 asks for ("detect model drift or bugs"); both reasons are carried too so an analyst
 * reading the audit trail doesn't have to cross-reference the original DECIDED entry to see why.
 */
public record ReconciliationResultEvent(
        String originalTransactionId,
        DecisionOutcome originalOutcome,
        DecisionOutcome newOutcome,
        BigDecimal originalRiskScore,
        BigDecimal newRiskScore,
        String originalReason,
        String newReason,
        boolean drifted,
        Instant checkedAt
) {
}
