package com.ringwatch.decision.priority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Hand-rolled array-backed binary min-heap (FR14's DSA centerpiece): {@code insert} and
 * {@code extractMin} run in O(log n) via sift-up/sift-down over an {@link ArrayList}.
 *
 * <p>Deliberately not thread-safe: this class is a plain data structure, not a Spring bean.
 * {@link com.ringwatch.decision.priority.DecisionPriorityQueue} is the shared, synchronized
 * component that wraps one instance for concurrent producer (Kafka listener thread) / consumer
 * (decision worker thread) access — mirroring how {@code LruCache} in enrichment-service keeps
 * its own synchronization at the component boundary rather than in a lower-level helper.
 *
 * <p>To prioritize "highest value first" (e.g. highest fraud risk score first), construct with a
 * {@link Comparator} that reverses the natural ordering of the field being prioritized —
 * {@code extractMin} then returns whichever element is "smallest" under that reversed
 * comparator, which is the element with the highest underlying value.
 */
public final class MinHeap<E> {

    private final List<E> elements = new ArrayList<>();
    private final Comparator<? super E> comparator;

    public MinHeap(Comparator<? super E> comparator) {
        this.comparator = comparator;
    }

    public void insert(E element) {
        elements.add(element);
        siftUp(elements.size() - 1);
    }

    public E extractMin() {
        if (elements.isEmpty()) {
            throw new NoSuchElementException("heap is empty");
        }
        E min = elements.get(0);
        E last = elements.remove(elements.size() - 1);
        if (!elements.isEmpty()) {
            elements.set(0, last);
            siftDown(0);
        }
        return min;
    }

    public E peek() {
        if (elements.isEmpty()) {
            throw new NoSuchElementException("heap is empty");
        }
        return elements.get(0);
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    private void siftUp(int index) {
        while (index > 0) {
            int parent = (index - 1) / 2;
            if (comparator.compare(elements.get(index), elements.get(parent)) >= 0) {
                break;
            }
            swap(index, parent);
            index = parent;
        }
    }

    private void siftDown(int index) {
        int size = elements.size();
        while (true) {
            int left = 2 * index + 1;
            int right = 2 * index + 2;
            int smallest = index;
            if (left < size && comparator.compare(elements.get(left), elements.get(smallest)) < 0) {
                smallest = left;
            }
            if (right < size && comparator.compare(elements.get(right), elements.get(smallest)) < 0) {
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
        E temp = elements.get(i);
        elements.set(i, elements.get(j));
        elements.set(j, temp);
    }
}
