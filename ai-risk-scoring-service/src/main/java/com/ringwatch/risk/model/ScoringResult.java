package com.ringwatch.risk.model;

import com.ringwatch.common.event.ScoringMethod;
import java.math.BigDecimal;

public record ScoringResult(BigDecimal score, String explanation, ScoringMethod method) {
}
