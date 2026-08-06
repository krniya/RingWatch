package com.ringwatch.common.event;

import java.time.Instant;
import java.util.Set;

public record FraudRingEvent(
        String ringId,
        Set<String> memberAccountIds,
        String sharedAttributes,
        String aiExplanation,
        Instant detectedAt
) {
}
