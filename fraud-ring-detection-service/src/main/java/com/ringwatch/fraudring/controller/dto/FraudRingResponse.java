package com.ringwatch.fraudring.controller.dto;

import com.ringwatch.fraudring.model.FraudRingDetection;
import java.time.Instant;
import java.util.Set;

public record FraudRingResponse(
        String ringId,
        Set<String> memberAccountIds,
        String sharedAttributes,
        String aiExplanation,
        Instant detectedAt
) {
    public static FraudRingResponse from(FraudRingDetection detection) {
        return new FraudRingResponse(
                detection.getRingId(),
                detection.getMemberAccountIds(),
                detection.getSharedAttributes(),
                detection.getAiExplanation(),
                detection.getDetectedAt());
    }
}
