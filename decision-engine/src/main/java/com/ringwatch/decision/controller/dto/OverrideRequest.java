package com.ringwatch.decision.controller.dto;

import com.ringwatch.common.event.DecisionOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OverrideRequest(
        @NotNull DecisionOutcome outcome,
        @NotBlank String reason
) {
}
