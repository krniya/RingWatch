package com.ringwatch.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record DecisionEvent(
        String transactionId,
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
        ScoringMethod scoringMethod,
        DecisionOutcome outcome,
        String reason,
        Instant decidedAt
) {
}
