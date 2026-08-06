package com.ringwatch.decision.service;

import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.decision.model.DecisionResult;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Applies FR13's threshold rules to a scored transaction to reach an APPROVE/FLAG/BLOCK
 * decision. Fraud ring membership is deliberately not yet a factor here: the Fraud Ring
 * Detection Service (Phase 3) doesn't exist yet, so there is no ring-membership signal to
 * combine in. When that service ships, its output is expected to arrive as an additional input
 * this engine escalates on, not a replacement for the risk-score thresholds below.
 */
@Component
public class RuleBasedDecisionEngine {

    private final BigDecimal blockThreshold;
    private final BigDecimal flagThreshold;

    public RuleBasedDecisionEngine(
            @Value("${ringwatch.decision.block-threshold:0.75}") BigDecimal blockThreshold,
            @Value("${ringwatch.decision.flag-threshold:0.40}") BigDecimal flagThreshold) {
        this.blockThreshold = blockThreshold;
        this.flagThreshold = flagThreshold;
    }

    public DecisionResult decide(ScoredTransactionEvent event) {
        BigDecimal score = event.riskScore();
        if (score.compareTo(blockThreshold) >= 0) {
            return new DecisionResult(DecisionOutcome.BLOCK,
                    reason(event, "meets or exceeds the block threshold", blockThreshold));
        }
        if (score.compareTo(flagThreshold) >= 0) {
            return new DecisionResult(DecisionOutcome.FLAG,
                    reason(event, "meets or exceeds the flag threshold", flagThreshold));
        }
        return new DecisionResult(DecisionOutcome.APPROVE,
                reason(event, "is below the flag threshold", flagThreshold));
    }

    private static String reason(ScoredTransactionEvent event, String comparison, BigDecimal threshold) {
        return "Risk score %s (%s) %s %s. %s".formatted(
                event.riskScore(), event.scoringMethod(), comparison, threshold, event.explanation());
    }
}
