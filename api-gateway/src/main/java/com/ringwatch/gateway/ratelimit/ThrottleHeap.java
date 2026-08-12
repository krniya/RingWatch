package com.ringwatch.gateway.ratelimit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-rolled indexed min-heap (FR17's DSA centerpiece) maintaining the current top-{@code
 * capacity} most-throttled rate-limit keys. Unlike decision-engine's {@code MinHeap} (insert +
 * extractMin only), entries here need their count increased in place as the same key gets
 * throttled again - {@code indexOf} gives O(1) lookup of an existing entry's heap position so
 * {@code observe} can update+resift in O(log n) instead of scanning.
 *
 * <p>{@code observe} takes each key's already-computed cumulative count (tracked by the caller,
 * {@link ThrottledKeyTracker}), not just "throttled once more" - a not-yet-tracked key can only
 * displace the heap's current minimum once its own cumulative count exceeds it, which a brand-new
 * key (cumulative count 1) could never do against an already-full heap of established offenders.
 * Without tracking true cumulative counts outside the heap, a bounded top-K structure would let
 * whichever {@code capacity} keys happened to be throttled first permanently occupy every slot,
 * even if a later-arriving repeat offender should clearly outrank them.
 *
 * <p>Not thread-safe by itself; {@link ThrottledKeyTracker} is the synchronized component wrapping
 * one instance, mirroring {@code DecisionPriorityQueue}'s role for {@code MinHeap}.
 */
final class ThrottleHeap {

    private final List<ThrottleCount> heap = new ArrayList<>();
    private final Map<String, Integer> indexOf = new HashMap<>();
    private final int capacity;

    ThrottleHeap(int capacity) {
        this.capacity = capacity;
    }

    void observe(String key, long newCumulativeCount) {
        if (capacity <= 0) {
            return;
        }
        Integer index = indexOf.get(key);
        if (index != null) {
            heap.set(index, new ThrottleCount(key, newCumulativeCount));
            siftDown(index);
            return;
        }
        if (heap.size() < capacity) {
            insert(new ThrottleCount(key, newCumulativeCount));
            return;
        }
        if (newCumulativeCount > heap.get(0).count()) {
            evictMin();
            insert(new ThrottleCount(key, newCumulativeCount));
        }
    }

    List<ThrottleCount> topByCountDescending() {
        List<ThrottleCount> sorted = new ArrayList<>(heap);
        sorted.sort(Comparator.comparingLong(ThrottleCount::count).reversed());
        return List.copyOf(sorted);
    }

    private void insert(ThrottleCount entry) {
        heap.add(entry);
        int index = heap.size() - 1;
        indexOf.put(entry.key(), index);
        siftUp(index);
    }

    private void evictMin() {
        ThrottleCount min = heap.get(0);
        indexOf.remove(min.key());
        ThrottleCount last = heap.remove(heap.size() - 1);
        if (!heap.isEmpty()) {
            heap.set(0, last);
            indexOf.put(last.key(), 0);
            siftDown(0);
        }
    }

    private void siftUp(int index) {
        while (index > 0) {
            int parent = (index - 1) / 2;
            if (heap.get(index).count() >= heap.get(parent).count()) {
                break;
            }
            swap(index, parent);
            index = parent;
        }
    }

    private void siftDown(int index) {
        int size = heap.size();
        while (true) {
            int left = 2 * index + 1;
            int right = 2 * index + 2;
            int smallest = index;
            if (left < size && heap.get(left).count() < heap.get(smallest).count()) {
                smallest = left;
            }
            if (right < size && heap.get(right).count() < heap.get(smallest).count()) {
                smallest = right;
            }
            if (smallest == index) {
                break;
            }
            swap(index, smallest);
            index = smallest;
        }
    }

    private void swap(int i, int j) {
        ThrottleCount temp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, temp);
        indexOf.put(heap.get(i).key(), i);
        indexOf.put(heap.get(j).key(), j);
    }
}
