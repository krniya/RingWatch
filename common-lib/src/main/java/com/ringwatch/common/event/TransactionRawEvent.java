package com.ringwatch.common.event;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRawEvent(
        String transactionId,
        String senderAccountId,
        String receiverAccountId,
        BigDecimal amount,
        String currency,
        String deviceId,
        String ipAddress,
        Instant timestamp
) {
}
