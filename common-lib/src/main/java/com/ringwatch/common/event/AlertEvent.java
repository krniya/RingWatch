package com.ringwatch.common.event;

import java.time.Instant;

public record AlertEvent(
        String alertId,
        AlertType alertType,
        String transactionId,
        String ringId,
        String message,
        Instant createdAt) {
}
