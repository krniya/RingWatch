package com.ringwatch.reconciliation.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringwatch.common.event.DecisionOutcome;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CorrelationStoreTest {

    @Test
    void putThenRemoveReturnsTheStoredEntry() {
        CorrelationStore store = new CorrelationStore();
        PendingReconciliation pending = new PendingReconciliation(
                "tx-1", DecisionOutcome.APPROVE, new BigDecimal("0.10"), "some reason", Instant.now());

        store.put("correlation-1", pending);

        assertThat(store.remove("correlation-1")).isEqualTo(pending);
    }

    @Test
    void removeIsIdempotentAndReturnsNullOnceConsumed() {
        CorrelationStore store = new CorrelationStore();
        store.put("correlation-1", new PendingReconciliation(
                "tx-1", DecisionOutcome.APPROVE, new BigDecimal("0.10"), "some reason", Instant.now()));

        store.remove("correlation-1");

        assertThat(store.remove("correlation-1")).isNull();
    }

    @Test
    void removeOfAnUnknownCorrelationIdReturnsNull() {
        CorrelationStore store = new CorrelationStore();

        assertThat(store.remove("never-put")).isNull();
    }

    @Test
    void evictStaleEntriesRemovesOnlyEntriesOlderThanTheMaxPendingAge() {
        CorrelationStore store = new CorrelationStore();
        store.put("stale", new PendingReconciliation(
                "tx-stale", DecisionOutcome.APPROVE, new BigDecimal("0.10"), "some reason",
                Instant.now().minus(Duration.ofMinutes(11))));
        store.put("fresh", new PendingReconciliation(
                "tx-fresh", DecisionOutcome.APPROVE, new BigDecimal("0.10"), "some reason", Instant.now()));

        store.evictStaleEntries();

        assertThat(store.remove("stale")).isNull();
        assertThat(store.remove("fresh")).isNotNull();
    }
}
