package com.ringwatch.decision.controller.dto;

import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.decision.model.Decision;
import java.time.Instant;

public record DecisionResponse(
        String transactionId,
        DecisionOutcome outcome,
        String reason,
        String overriddenBy,
        String overrideReason,
        Instant createdAt
) {
    public static DecisionResponse from(Decision decision) {
        return new DecisionResponse(
                decision.getTransactionId(),
                decision.getOutcome(),
                decision.getReason(),
                decision.getOverriddenBy(),
                decision.getOverrideReason(),
                decision.getCreatedAt());
    }
}
