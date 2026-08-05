package com.ringwatch.risk.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.risk.model.ScoringResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleBasedRiskScorerTest {

    private final RuleBasedRiskScorer scorer = new RuleBasedRiskScorer();

    private static EnrichedTransactionEvent event(
            int recentTxnCount, BigDecimal avgTxnAmount, BigDecimal amount,
            String deviceId, Set<String> knownDevices, String ipAddress, Set<String> knownIps) {
        return new EnrichedTransactionEvent(
                "tx-1", "sender-1", "receiver-1", amount, "USD", deviceId, ipAddress,
                Instant.now(), recentTxnCount, avgTxnAmount, knownDevices, knownIps);
    }

    @Test
    void noRiskIndicatorsProducesZeroScore() {
        ScoringResult result = scorer.score(event(
                3, new BigDecimal("100.00"), new BigDecimal("120.00"),
                "device-1", Set.of("device-1"), "10.0.0.1", Set.of("10.0.0.1")));

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.method()).isEqualTo(ScoringMethod.RULE_FALLBACK);
        assertThat(result.explanation()).contains("no risk indicators triggered");
    }

    @Test
    void noPriorHistoryAddsWeight() {
        ScoringResult result = scorer.score(event(
                0, BigDecimal.ZERO, new BigDecimal("50.00"),
                "device-1", Set.of(), "10.0.0.1", Set.of()));

        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(result.explanation()).contains("no prior transaction history");
    }

    @Test
    void amountFarAboveAverageAddsWeight() {
        ScoringResult result = scorer.score(event(
                5, new BigDecimal("100.00"), new BigDecimal("600.00"),
                "device-1", Set.of("device-1"), "10.0.0.1", Set.of("10.0.0.1")));

        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("0.35"));
        assertThat(result.explanation()).contains("more than 5x the sender's average transaction");
    }

    @Test
    void amountExactlyAtFiveTimesAverageDoesNotTrigger() {
        ScoringResult result = scorer.score(event(
                5, new BigDecimal("100.00"), new BigDecimal("500.00"),
                "device-1", Set.of("device-1"), "10.0.0.1", Set.of("10.0.0.1")));

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void unknownDeviceAddsWeight() {
        ScoringResult result = scorer.score(event(
                5, new BigDecimal("100.00"), new BigDecimal("100.00"),
                "device-new", Set.of("device-1"), "10.0.0.1", Set.of("10.0.0.1")));

        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("0.30"));
        assertThat(result.explanation()).contains("device is not among the sender's known devices");
    }

    @Test
    void unknownIpAddsWeight() {
        ScoringResult result = scorer.score(event(
                5, new BigDecimal("100.00"), new BigDecimal("100.00"),
                "device-1", Set.of("device-1"), "10.0.0.99", Set.of("10.0.0.1")));

        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(result.explanation()).contains("IP address is not among the sender's known IPs");
    }

    @Test
    void multipleIndicatorsAccumulateAndClampAtOne() {
        ScoringResult result = scorer.score(event(
                0, new BigDecimal("50.00"), new BigDecimal("1000.00"),
                "device-new", Set.of("device-1"), "10.0.0.99", Set.of("10.0.0.1")));

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.explanation())
                .contains("no prior transaction history")
                .contains("average transaction")
                .contains("known devices")
                .contains("known IPs");
    }
}
