package com.ringwatch.reconciliation.correlation;

import com.ringwatch.common.event.DecisionOutcome;
import java.math.BigDecimal;
import java.time.Instant;

/** The original decision's data, held in memory until the isolated re-decision comes back. */
public record PendingReconciliation(
        String originalTransactionId,
        DecisionOutcome originalOutcome,
        BigDecimal originalRiskScore,
        String originalReason,
        Instant sampledAt
) {
}
