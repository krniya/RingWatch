package com.ringwatch.enrichment.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Fixed-capacity LRU cache: HashMap for O(1) lookup plus a doubly linked list for O(1) recency
 * reordering and eviction. Locking is a single monitor covering the whole structure (not per-key,
 * unlike {@code SlidingWindowRateLimiter} in api-gateway) because recency order is one shared
 * list across all keys — a get() on any key mutates global list state.
 */
public class LruCache<K, V> {

    private final int capacity;
    private final Map<K, Node<K, V>> nodes = new HashMap<>();
    private final Node<K, V> head = new Node<>(null, null);
    private final Node<K, V> tail = new Node<>(null, null);

    public LruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public synchronized Optional<V> get(K key) {
        Node<K, V> node = nodes.get(key);
        if (node == null) {
            return Optional.empty();
        }
        moveToFront(node);
        return Optional.of(node.value);
    }

    public synchronized void put(K key, V value) {
        Node<K, V> existing = nodes.get(key);
        if (existing != null) {
            existing.value = value;
            moveToFront(existing);
            return;
        }

        Node<K, V> node = new Node<>(key, value);
        nodes.put(key, node);
        addToFront(node);

        if (nodes.size() > capacity) {
            Node<K, V> leastRecentlyUsed = tail.prev;
            removeNode(leastRecentlyUsed);
            nodes.remove(leastRecentlyUsed.key);
        }
    }

    /**
     * Atomically reads the current value for {@code key} (or {@code defaultValue} if absent),
     * applies {@code remapper} to it, stores the result, and returns the value as it was
     * <em>before</em> the update. Callers that would otherwise compose {@link #get} and
     * {@link #put} into a read-modify-write sequence should use this instead — those two calls
     * are individually synchronized but not atomic together, so interleaved callers could each
     * read the same stale value and the later {@code put} would silently clobber the earlier
     * update. Java's intrinsic locks are reentrant, so calling the synchronized get/put from
     * within this synchronized method still holds the lock for the whole sequence.
     */
    public synchronized V computeAndPut(K key, V defaultValue, UnaryOperator<V> remapper) {
        V previous = get(key).orElse(defaultValue);
        put(key, remapper.apply(previous));
        return previous;
    }

    public synchronized int size() {
        return nodes.size();
    }

    private void moveToFront(Node<K, V> node) {
        removeNode(node);
        addToFront(node);
    }

    private void addToFront(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private static final class Node<K, V> {
        final K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}
