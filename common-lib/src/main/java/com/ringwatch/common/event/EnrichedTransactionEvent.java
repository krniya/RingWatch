package com.ringwatch.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record EnrichedTransactionEvent(
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
        Set<String> knownIps
) {
}
