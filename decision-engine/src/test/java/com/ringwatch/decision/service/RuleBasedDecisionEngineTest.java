package com.ringwatch.decision.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.decision.model.DecisionResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleBasedDecisionEngineTest {

    private final RuleBasedDecisionEngine engine =
            new RuleBasedDecisionEngine(new BigDecimal("0.75"), new BigDecimal("0.40"));

    private static ScoredTransactionEvent eventWithScore(String riskScore) {
        return new ScoredTransactionEvent(
                "tx-1", "sender-1", "receiver-1", new BigDecimal("500.00"), "USD",
                "device-1", "10.0.0.1", Instant.now(), 3, new BigDecimal("50.00"),
                Set.of("device-1"), Set.of("10.0.0.1"),
                new BigDecimal(riskScore), "some explanation", ScoringMethod.AI);
    }

    @Test
    void scoreBelowFlagThresholdIsApproved() {
        DecisionResult result = engine.decide(eventWithScore("0.10"));

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.APPROVE);
        assertThat(result.reason()).contains("0.10").contains("below the flag threshold");
    }

    @Test
    void scoreAtFlagThresholdIsFlaggedNotApproved() {
        DecisionResult result = engine.decide(eventWithScore("0.40"));

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.FLAG);
    }

    @Test
    void scoreBetweenThresholdsIsFlagged() {
        DecisionResult result = engine.decide(eventWithScore("0.60"));

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.FLAG);
        assertThat(result.reason()).contains("0.60").contains("flag threshold");
    }

    @Test
    void scoreAtBlockThresholdIsBlockedNotFlagged() {
        DecisionResult result = engine.decide(eventWithScore("0.75"));

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.BLOCK);
    }

    @Test
    void scoreAboveBlockThresholdIsBlocked() {
        DecisionResult result = engine.decide(eventWithScore("0.95"));

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.BLOCK);
        assertThat(result.reason()).contains("0.95").contains("block threshold");
    }

    @Test
    void reasonIncludesScoringMethodAndAiExplanation() {
        DecisionResult result = engine.decide(eventWithScore("0.80"));

        assertThat(result.reason()).contains("AI").contains("some explanation");
    }
}
