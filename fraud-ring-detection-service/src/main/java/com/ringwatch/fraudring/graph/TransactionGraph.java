package com.ringwatch.fraudring.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Directed transaction graph (accountId -&gt; accounts it has sent funds to) with BFS-based cycle
 * detection (FR11): before adding an edge sender -&gt; receiver, checks whether receiver can
 * already reach sender via existing edges. If so, adding this edge would close a cycle
 * (sender -&gt; receiver -&gt; ... -&gt; sender) - exactly the "circular fund movement" pattern FR11
 * describes - so the check runs against the graph as it stood *before* this transaction.
 *
 * <p>Not thread-safe by itself, mirroring {@link UnionFind}; {@code FraudRingDetectionService}
 * only ever calls this from the single Kafka listener thread, but methods are still synchronized
 * defensively, matching the same posture already applied to {@link AccountClusterGraph}.
 */
@Component
public class TransactionGraph {

    private final Map<String, Set<String>> outgoingEdges = new HashMap<>();

    public synchronized Optional<List<String>> recordTransferAndDetectCycle(String sender, String receiver) {
        if (sender.equals(receiver)) {
            // A single self-transfer isn't the "movement through intermediaries" FR11 describes;
            // record the edge but don't trivially "detect" a cycle on every self-transaction.
            outgoingEdges.computeIfAbsent(sender, k -> new LinkedHashSet<>()).add(receiver);
            return Optional.empty();
        }

        List<String> pathBackToSender = bfsPath(receiver, sender);
        outgoingEdges.computeIfAbsent(sender, k -> new LinkedHashSet<>()).add(receiver);
        if (pathBackToSender == null) {
            return Optional.empty();
        }
        List<String> cycle = new ArrayList<>();
        cycle.add(sender);
        cycle.addAll(pathBackToSender);
        return Optional.of(cycle);
    }

    private List<String> bfsPath(String start, String target) {
        if (start.equals(target)) {
            return List.of(start);
        }
        Map<String, String> cameFrom = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : outgoingEdges.getOrDefault(current, Set.of())) {
                if (visited.contains(next)) {
                    continue;
                }
                visited.add(next);
                cameFrom.put(next, current);
                if (next.equals(target)) {
                    return reconstructPath(start, target, cameFrom);
                }
                queue.add(next);
            }
        }
        return null;
    }

    private static List<String> reconstructPath(String start, String target, Map<String, String> cameFrom) {
        List<String> path = new ArrayList<>();
        String current = target;
        while (!current.equals(start)) {
            path.add(0, current);
            current = cameFrom.get(current);
        }
        path.add(0, start);
        return path;
    }
}
