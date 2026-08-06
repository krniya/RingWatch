package com.ringwatch.fraudring.graph;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link UnionFind} to cluster accounts connected via shared devices, shared IPs, or
 * direct fund transfers (FR9/FR10). Every transaction contributes up to three union operations -
 * sender-receiver (a transfer edge), sender-device, sender-IP - so two accounts end up in the
 * same cluster whenever they're linked by any chain of these relationships, not just a single
 * directly shared value.
 *
 * <p>Cluster membership is derived on demand by scanning every known account ID and grouping by
 * {@code find()} on the transaction's sender - O(known accounts) per transaction. That's a
 * deliberate, demo-scale simplification, not an oversight: a system with many thousands of
 * accounts would want an incrementally-maintained root-to-members index instead of re-deriving
 * membership from scratch on every call.
 *
 * <p>{@link #observe} only returns a result the first time a cluster's member count grows past
 * its previously-published size, so a cluster that already crossed the threshold doesn't
 * re-publish on every subsequent transaction among its existing members - only on genuine growth.
 */
@Component
public class AccountClusterGraph {

    private final UnionFind unionFind = new UnionFind();
    private final Set<String> knownAccountIds = new HashSet<>();
    private final Map<String, Integer> lastPublishedSizeByRoot = new HashMap<>();
    private final int minClusterSize;

    public AccountClusterGraph(@Value("${ringwatch.fraud-ring.min-cluster-size:3}") int minClusterSize) {
        this.minClusterSize = minClusterSize;
    }

    public synchronized Optional<ClusterUpdate> observe(EnrichedTransactionEvent event) {
        String sender = event.senderAccountId();
        String receiver = event.receiverAccountId();
        knownAccountIds.add(sender);
        knownAccountIds.add(receiver);
        unionFind.union(sender, receiver);
        unionFind.union(sender, deviceKey(event.deviceId()));
        unionFind.union(sender, ipKey(event.ipAddress()));

        String root = unionFind.find(sender);
        Set<String> members = new HashSet<>();
        for (String accountId : knownAccountIds) {
            if (unionFind.find(accountId).equals(root)) {
                members.add(accountId);
            }
        }

        int previouslyPublished = lastPublishedSizeByRoot.getOrDefault(root, 0);
        if (members.size() < minClusterSize || members.size() <= previouslyPublished) {
            return Optional.empty();
        }
        lastPublishedSizeByRoot.put(root, members.size());
        return Optional.of(new ClusterUpdate(members, event.deviceId(), event.ipAddress()));
    }

    private static String deviceKey(String deviceId) {
        return "device:" + deviceId;
    }

    private static String ipKey(String ipAddress) {
        return "ip:" + ipAddress;
    }
}
