package com.ringwatch.decision.service;

import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.decision.model.DecisionResult;
import com.ringwatch.decision.ring.RingMembership;
import com.ringwatch.decision.ring.RingMembershipRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Applies FR13's rules to a scored transaction to reach an APPROVE/FLAG/BLOCK decision: the
 * risk-score thresholds first, then an escalation step if either account involved is a known
 * fraud ring member (per {@link RingMembershipRegistry}, kept current by
 * {@link com.ringwatch.decision.kafka.RingFlagListener}). Ring membership never de-escalates a
 * decision the thresholds already reached on their own - it only pushes APPROVE up to FLAG or
 * FLAG up to BLOCK; a BLOCK stands regardless.
 *
 * <p><b>Known limitation (accepted, not a bug):</b> {@code transactions.ring-flagged} and
 * {@code transactions.scored} are independent consumers with no ordering guarantee between them,
 * so a transaction from an account that's about to be flagged as a ring member can still be
 * decided - and APPROVEd - if it's processed before that ring-flag event is consumed. There is no
 * mechanism to revisit an already-published decision once the ring flag later arrives; closing
 * that gap would mean re-scoring/replaying past decisions, which is out of scope here.
 */
@Component
public class RuleBasedDecisionEngine {

    private final BigDecimal blockThreshold;
    private final BigDecimal flagThreshold;
    private final RingMembershipRegistry ringMembershipRegistry;

    public RuleBasedDecisionEngine(
            @Value("${ringwatch.decision.block-threshold:0.75}") BigDecimal blockThreshold,
            @Value("${ringwatch.decision.flag-threshold:0.40}") BigDecimal flagThreshold,
            RingMembershipRegistry ringMembershipRegistry) {
        this.blockThreshold = blockThreshold;
        this.flagThreshold = flagThreshold;
        this.ringMembershipRegistry = ringMembershipRegistry;
    }

    public DecisionResult decide(ScoredTransactionEvent event) {
        DecisionOutcome outcome;
        String reason;
        BigDecimal score = event.riskScore();
        if (score.compareTo(blockThreshold) >= 0) {
            outcome = DecisionOutcome.BLOCK;
            reason = reason(event, "meets or exceeds the block threshold", blockThreshold);
        } else if (score.compareTo(flagThreshold) >= 0) {
            outcome = DecisionOutcome.FLAG;
            reason = reason(event, "meets or exceeds the flag threshold", flagThreshold);
        } else {
            outcome = DecisionOutcome.APPROVE;
            reason = reason(event, "is below the flag threshold", flagThreshold);
        }

        if (outcome == DecisionOutcome.BLOCK) {
            return new DecisionResult(outcome, reason);
        }
        Optional<RingMembership> ringMembership = ringMembershipOf(event);
        if (ringMembership.isEmpty()) {
            return new DecisionResult(outcome, reason);
        }
        DecisionOutcome escalated = outcome == DecisionOutcome.APPROVE ? DecisionOutcome.FLAG : DecisionOutcome.BLOCK;
        RingMembership membership = ringMembership.get();
        String escalatedReason = "%s Escalated from %s to %s: an account on this transaction belongs to fraud ring '%s' (%s)."
                .formatted(reason, outcome, escalated, membership.ringId(), membership.sharedAttributes());
        return new DecisionResult(escalated, escalatedReason);
    }

    /**
     * Checks the sender first, then the receiver; only the first match is returned. If sender
     * and receiver belong to two different rings, the escalation still fires but the reason text
     * only names the sender's ring - a deliberate simplification (only ring *presence* affects
     * the outcome), not an attempt to summarize every ring either account belongs to.
     */
    private Optional<RingMembership> ringMembershipOf(ScoredTransactionEvent event) {
        Optional<RingMembership> sender = ringMembershipRegistry.membershipOf(event.senderAccountId());
        return sender.isPresent() ? sender : ringMembershipRegistry.membershipOf(event.receiverAccountId());
    }

    private static String reason(ScoredTransactionEvent event, String comparison, BigDecimal threshold) {
        return "Risk score %s (%s) %s %s. %s".formatted(
                event.riskScore(), event.scoringMethod(), comparison, threshold, event.explanation());
    }
}
