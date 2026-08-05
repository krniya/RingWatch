package com.ringwatch.risk.service;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.risk.model.ScoringResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic fallback used when the LLM risk scorer is unavailable (FR8). Applies simple,
 * explainable heuristics against the historical context the enrichment service attached to the
 * transaction, rather than leaving the pipeline without a score.
 *
 * <p>As the last line of defense — including for LLM-path failures caused by a malformed
 * {@link EnrichedTransactionEvent}, since the same event is what {@link LlmRiskScorer} failed on
 * before falling back here — the historical fields are treated as optional rather than trusted,
 * so this scorer can't itself throw on the exact input that already broke the AI path.
 */
@Component
public class RuleBasedRiskScorer {

    private static final BigDecimal AMOUNT_SPIKE_MULTIPLIER = BigDecimal.valueOf(5);
    private static final BigDecimal NO_HISTORY_WEIGHT = new BigDecimal("0.10");
    private static final BigDecimal AMOUNT_SPIKE_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal NEW_DEVICE_WEIGHT = new BigDecimal("0.30");
    private static final BigDecimal NEW_IP_WEIGHT = new BigDecimal("0.25");

    public ScoringResult score(EnrichedTransactionEvent event) {
        BigDecimal avgTxnAmount = event.avgTxnAmount() != null ? event.avgTxnAmount() : BigDecimal.ZERO;
        Set<String> knownDevices = event.knownDevices() != null ? event.knownDevices() : Set.of();
        Set<String> knownIps = event.knownIps() != null ? event.knownIps() : Set.of();

        BigDecimal score = BigDecimal.ZERO;
        List<String> reasons = new ArrayList<>();

        if (event.recentTxnCount() == 0) {
            score = score.add(NO_HISTORY_WEIGHT);
            reasons.add("sender has no prior transaction history");
        }

        if (avgTxnAmount.compareTo(BigDecimal.ZERO) > 0
                && event.amount().compareTo(avgTxnAmount.multiply(AMOUNT_SPIKE_MULTIPLIER)) > 0) {
            score = score.add(AMOUNT_SPIKE_WEIGHT);
            reasons.add("amount is more than " + AMOUNT_SPIKE_MULTIPLIER + "x the sender's average transaction");
        }

        if (!knownDevices.isEmpty() && !knownDevices.contains(event.deviceId())) {
            score = score.add(NEW_DEVICE_WEIGHT);
            reasons.add("device is not among the sender's known devices");
        }

        if (!knownIps.isEmpty() && !knownIps.contains(event.ipAddress())) {
            score = score.add(NEW_IP_WEIGHT);
            reasons.add("IP address is not among the sender's known IPs");
        }

        BigDecimal clamped = score.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
        String explanation = reasons.isEmpty()
                ? "Rule-based fallback: no risk indicators triggered."
                : "Rule-based fallback: " + String.join("; ", reasons) + ".";
        return new ScoringResult(clamped, explanation, ScoringMethod.RULE_FALLBACK);
    }
}
