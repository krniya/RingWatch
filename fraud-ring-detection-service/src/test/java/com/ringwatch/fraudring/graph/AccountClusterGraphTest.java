package com.ringwatch.fraudring.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountClusterGraphTest {

    private final AccountClusterGraph graph = new AccountClusterGraph(3);

    private static EnrichedTransactionEvent event(
            String transactionId, String sender, String receiver, String deviceId, String ipAddress) {
        return new EnrichedTransactionEvent(
                transactionId, sender, receiver, new BigDecimal("100.00"), "USD",
                deviceId, ipAddress, Instant.now(), 1, BigDecimal.TEN, Set.of(), Set.of());
    }

    @Test
    void twoAccountClusterBelowThresholdProducesNoUpdate() {
        Optional<ClusterUpdate> update = graph.observe(event("tx-1", "A", "B", "dev1", "ip1"));

        assertThat(update).isEmpty();
    }

    @Test
    void clusterCrossingThresholdViaSharedDeviceProducesUpdateWithAllMembers() {
        graph.observe(event("tx-1", "A", "B", "dev1", "ip1"));

        Optional<ClusterUpdate> update = graph.observe(event("tx-2", "C", "D", "dev1", "ip2"));

        assertThat(update).isPresent();
        assertThat(update.get().memberAccountIds()).containsExactlyInAnyOrder("A", "B", "C", "D");
        assertThat(update.get().triggeringDeviceId()).isEqualTo("dev1");
        assertThat(update.get().triggeringIpAddress()).isEqualTo("ip2");
    }

    @Test
    void clusterCrossingThresholdViaSharedIpProducesUpdate() {
        graph.observe(event("tx-1", "A", "B", "devA", "ipShared"));

        Optional<ClusterUpdate> update = graph.observe(event("tx-2", "C", "D", "devC", "ipShared"));

        assertThat(update).isPresent();
        assertThat(update.get().memberAccountIds()).containsExactlyInAnyOrder("A", "B", "C", "D");
    }

    @Test
    void sameClusterSizeDoesNotRepublish() {
        graph.observe(event("tx-1", "A", "B", "dev1", "ip1"));
        graph.observe(event("tx-2", "C", "D", "dev1", "ip2"));

        Optional<ClusterUpdate> update = graph.observe(event("tx-3", "A", "C", "dev1", "ip1"));

        assertThat(update).isEmpty();
    }

    @Test
    void clusterGrowingFurtherRepublishesWithUpdatedMembers() {
        graph.observe(event("tx-1", "A", "B", "dev1", "ip1"));
        graph.observe(event("tx-2", "C", "D", "dev1", "ip2"));

        Optional<ClusterUpdate> update = graph.observe(event("tx-3", "E", "F", "dev1", "ip3"));

        assertThat(update).isPresent();
        assertThat(update.get().memberAccountIds()).containsExactlyInAnyOrder("A", "B", "C", "D", "E", "F");
    }

    @Test
    void unrelatedAccountsWithNoSharedAttributesStayInSeparateClusters() {
        graph.observe(event("tx-1", "A", "B", "dev1", "ip1"));
        graph.observe(event("tx-2", "C", "D", "dev1", "ip2"));

        // Grow a second, entirely unrelated cluster past the threshold too, and confirm its
        // reported membership never picks up A/B/C/D - proving the two clusters never merged,
        // not merely that this particular observation stayed below the size threshold.
        graph.observe(event("tx-3", "X", "Y", "dev-unrelated", "ip-unrelated"));
        Optional<ClusterUpdate> update = graph.observe(event("tx-4", "X", "Z", "dev-unrelated", "ip-unrelated-2"));

        assertThat(update).isPresent();
        assertThat(update.get().memberAccountIds()).containsExactlyInAnyOrder("X", "Y", "Z");
    }

    @Test
    void bridgingTwoIndependentlyPublishedClustersRepublishesWithTheMergedMembership() {
        // Each cluster crosses the threshold and gets published under its own root BEFORE the
        // two are ever linked, so lastPublishedSizeByRoot ends up with two separate entries
        // (one per root) prior to the merge - this exercises whether the post-merge lookup uses
        // the fresh surviving root's own baseline rather than a stale/wrong one.
        graph.observe(event("tx-1", "A", "B", "dev1", "ip1"));
        Optional<ClusterUpdate> firstClusterPublished = graph.observe(event("tx-2", "A", "C", "dev1", "ip1b"));
        assertThat(firstClusterPublished).isPresent();
        assertThat(firstClusterPublished.get().memberAccountIds()).containsExactlyInAnyOrder("A", "B", "C");

        graph.observe(event("tx-3", "D", "E", "dev2", "ip2"));
        Optional<ClusterUpdate> secondClusterPublished = graph.observe(event("tx-4", "D", "F", "dev2", "ip2b"));
        assertThat(secondClusterPublished).isPresent();
        assertThat(secondClusterPublished.get().memberAccountIds()).containsExactlyInAnyOrder("D", "E", "F");

        Optional<ClusterUpdate> merged = graph.observe(event("tx-5", "A", "D", "dev-bridge", "ip-bridge"));

        assertThat(merged).isPresent();
        assertThat(merged.get().memberAccountIds()).containsExactlyInAnyOrder("A", "B", "C", "D", "E", "F");
    }
}
