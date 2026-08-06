package com.ringwatch.decision.priority;

import com.ringwatch.common.event.ScoredTransactionEvent;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bounded, blocking priority buffer sitting between the Kafka listener thread ({@code
 * DecisionListener}, the producer) and the decision worker thread ({@code DecisionWorker}, the
 * consumer). Backed by {@link MinHeap} ordered by risk score descending, so under a load spike
 * — when the queue fills faster than the worker drains it — the highest-risk transactions are
 * always decided first (FR14), rather than strictly in arrival order.
 *
 * <p>{@code enqueue} blocks while the queue is at {@code capacity}, which both bounds memory and
 * applies natural backpressure: a full queue blocks the Kafka listener thread, which pauses
 * further polling until the worker catches up.
 *
 * <p>Decoupling consumption from processing this way trades some durability for the
 * prioritization behavior FR14 asks for: the Kafka offset for a message is committed as soon as
 * it's enqueued (not once it's actually decided), so a crash between enqueue and the worker
 * draining it loses that one in-flight decision. This is a deliberate, scoped tradeoff for this
 * slice rather than an oversight — closing it would mean persisting a durable pending-decision
 * marker per message before acking, which is a real piece of follow-up work, not a one-line fix.
 *
 * <p>Uses intrinsic locking ({@code synchronized}/{@code wait}/{@code notifyAll}) rather than
 * {@code java.util.concurrent} primitives, consistent with {@code LruCache}'s reentrant-lock
 * approach in enrichment-service.
 */
@Component
public class DecisionPriorityQueue {

    private final MinHeap<ScoredTransactionEvent> heap =
            new MinHeap<>(Comparator.comparing(ScoredTransactionEvent::riskScore).reversed());
    private final int capacity;

    public DecisionPriorityQueue(@Value("${ringwatch.decision.queue-capacity:1000}") int capacity) {
        this.capacity = capacity;
    }

    public synchronized void enqueue(ScoredTransactionEvent event) throws InterruptedException {
        while (heap.size() >= capacity) {
            wait();
        }
        heap.insert(event);
        notifyAll();
    }

    public synchronized ScoredTransactionEvent blockingDequeueHighestPriority() throws InterruptedException {
        while (heap.isEmpty()) {
            wait();
        }
        ScoredTransactionEvent event = heap.extractMin();
        notifyAll();
        return event;
    }

    public synchronized int size() {
        return heap.size();
    }
}
