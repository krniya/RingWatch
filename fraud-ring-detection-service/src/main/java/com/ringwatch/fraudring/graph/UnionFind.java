package com.ringwatch.fraudring.graph;

import java.util.HashMap;
import java.util.Map;

/**
 * Hand-rolled disjoint-set (Union-Find) with path compression and union by rank (FR10). Keys are
 * plain strings so the same structure can cluster together account IDs and synthetic
 * "attribute" keys (see {@link AccountClusterGraph}) without needing a typed node hierarchy.
 * Sets are created lazily: any key is implicitly its own singleton set the first time it's seen.
 *
 * <p>Not thread-safe by itself, mirroring {@code MinHeap} in decision-engine: this is a plain
 * data structure, not a Spring bean. {@link AccountClusterGraph} owns the synchronization for
 * concurrent access.
 */
public class UnionFind {

    private final Map<String, String> parent = new HashMap<>();
    private final Map<String, Integer> rank = new HashMap<>();

    public String find(String key) {
        makeSetIfAbsent(key);
        String root = key;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        String current = key;
        while (!current.equals(root)) {
            String next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

    public void union(String a, String b) {
        String rootA = find(a);
        String rootB = find(b);
        if (rootA.equals(rootB)) {
            return;
        }
        int rankA = rank.get(rootA);
        int rankB = rank.get(rootB);
        if (rankA < rankB) {
            parent.put(rootA, rootB);
        } else if (rankA > rankB) {
            parent.put(rootB, rootA);
        } else {
            parent.put(rootB, rootA);
            rank.put(rootA, rankA + 1);
        }
    }

    public boolean connected(String a, String b) {
        return find(a).equals(find(b));
    }

    private void makeSetIfAbsent(String key) {
        parent.putIfAbsent(key, key);
        rank.putIfAbsent(key, 0);
    }
}
