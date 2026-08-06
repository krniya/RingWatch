package com.ringwatch.decision.priority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.event.ScoringMethod;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DecisionPriorityQueueTest {

    private static ScoredTransactionEvent eventWithScore(String transactionId, String riskScore) {
        return new ScoredTransactionEvent(
                transactionId, "sender-1", "receiver-1", new BigDecimal("500.00"), "USD",
                "device-1", "10.0.0.1", Instant.now(), 3, new BigDecimal("50.00"),
                Set.of("device-1"), Set.of("10.0.0.1"),
                new BigDecimal(riskScore), "explanation", ScoringMethod.AI);
    }

    @Test
    void dequeueReturnsHighestRiskFirstRegardlessOfEnqueueOrder() throws InterruptedException {
        DecisionPriorityQueue queue = new DecisionPriorityQueue(10);
        queue.enqueue(eventWithScore("low", "0.10"));
        queue.enqueue(eventWithScore("high", "0.90"));
        queue.enqueue(eventWithScore("medium", "0.50"));

        assertThat(queue.blockingDequeueHighestPriority().transactionId()).isEqualTo("high");
        assertThat(queue.blockingDequeueHighestPriority().transactionId()).isEqualTo("medium");
        assertThat(queue.blockingDequeueHighestPriority().transactionId()).isEqualTo("low");
    }

    @Test
    void sizeTracksEnqueuedAndDequeuedElements() throws InterruptedException {
        DecisionPriorityQueue queue = new DecisionPriorityQueue(10);
        assertThat(queue.size()).isZero();

        queue.enqueue(eventWithScore("tx-1", "0.20"));
        queue.enqueue(eventWithScore("tx-2", "0.30"));
        assertThat(queue.size()).isEqualTo(2);

        queue.blockingDequeueHighestPriority();
        assertThat(queue.size()).isEqualTo(1);
    }

    @Test
    void dequeueBlocksUntilAnElementIsEnqueued() throws Exception {
        DecisionPriorityQueue queue = new DecisionPriorityQueue(10);
        AtomicReference<ScoredTransactionEvent> received = new AtomicReference<>();
        CountDownLatch waiting = new CountDownLatch(1);

        CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> {
            try {
                waiting.countDown();
                received.set(queue.blockingDequeueHighestPriority());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(waiting.await(2, TimeUnit.SECONDS)).isTrue();
        // Give the consumer thread a moment to actually reach the blocking wait() before we
        // enqueue, so this test genuinely exercises the block-then-wake path rather than racing.
        Thread.sleep(200);
        assertThat(consumer.isDone()).isFalse();

        queue.enqueue(eventWithScore("tx-1", "0.5"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(received.get()).isNotNull());
        assertThat(received.get().transactionId()).isEqualTo("tx-1");
    }

    @Test
    void enqueueBlocksWhenAtCapacityUntilSpaceIsFreed() throws Exception {
        DecisionPriorityQueue queue = new DecisionPriorityQueue(1);
        queue.enqueue(eventWithScore("tx-1", "0.5"));
        assertThat(queue.size()).isEqualTo(1);

        CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
            try {
                queue.enqueue(eventWithScore("tx-2", "0.6"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(200);
        assertThat(producer.isDone()).isFalse();
        assertThat(queue.size()).isEqualTo(1);

        queue.blockingDequeueHighestPriority();

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(producer.isDone()).isTrue());
        assertThat(queue.size()).isEqualTo(1);
    }
}
