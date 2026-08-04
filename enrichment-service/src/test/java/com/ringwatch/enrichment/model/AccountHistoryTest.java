package com.ringwatch.enrichment.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountHistoryTest {

    @Test
    void emptyHistoryHasZeroAverageAmount() {
        assertThat(AccountHistory.empty().averageAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void averageAmountRoundsNonTerminatingDecimalsHalfUpToTwoPlaces() {
        AccountHistory history = new AccountHistory(3, new BigDecimal("100.00"), Set.of(), Set.of());

        assertThat(history.averageAmount()).isEqualByComparingTo(new BigDecimal("33.33"));
    }

    @Test
    void averageAmountRoundsHalfUpAtTheTieBreakingDigit() {
        AccountHistory history = new AccountHistory(8, new BigDecimal("100.00"), Set.of(), Set.of());

        assertThat(history.averageAmount()).isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void withTransactionIncrementsCountAndAccumulatesTotalAndKnownAttributes() {
        AccountHistory updated = AccountHistory.empty().withTransaction("device-1", "10.0.0.1", new BigDecimal("50.00"));

        assertThat(updated.recentTxnCount()).isEqualTo(1);
        assertThat(updated.totalAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(updated.knownDevices()).containsExactly("device-1");
        assertThat(updated.knownIps()).containsExactly("10.0.0.1");
    }

    @Test
    void withTransactionDoesNotDuplicateAlreadyKnownDeviceOrIp() {
        AccountHistory first = AccountHistory.empty().withTransaction("device-1", "10.0.0.1", new BigDecimal("50.00"));
        AccountHistory second = first.withTransaction("device-1", "10.0.0.1", new BigDecimal("25.00"));

        assertThat(second.recentTxnCount()).isEqualTo(2);
        assertThat(second.knownDevices()).containsExactly("device-1");
        assertThat(second.knownIps()).containsExactly("10.0.0.1");
    }
}
