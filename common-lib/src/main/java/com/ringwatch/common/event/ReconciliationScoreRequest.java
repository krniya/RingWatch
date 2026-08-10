package com.ringwatch.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Published to {@code Topics.TRANSACTIONS_RECONCILIATION_SCORING_REQUESTED} (FR24): asks
 * ai-risk-scoring-service to re-score a past transaction's original enrichment inputs, without
 * touching the real {@code transactions.enriched}/{@code transactions.scored} topics or any live
 * stateful component. {@code correlationId} (not {@code originalTransactionId}) is what flows
 * downstream through the reconciliation-only topics, since it's what ties this request back to
 * the eventual {@link ReconciliationDecisionResult} - {@code originalTransactionId} rides along
 * purely so consumers can attach it to the final result without a separate lookup.
 */
public record ReconciliationScoreRequest(
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
        Set<String> knownIps
) {
}
