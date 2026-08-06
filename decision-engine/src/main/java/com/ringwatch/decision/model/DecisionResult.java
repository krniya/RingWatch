package com.ringwatch.decision.model;

import com.ringwatch.common.event.DecisionOutcome;

public record DecisionResult(DecisionOutcome outcome, String reason) {
}
