package com.ringwatch.decision.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.decision.priority.DecisionPriorityQueue;
import com.ringwatch.decision.service.DecisionService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecisionWorkerTest {

    @Mock private DecisionPriorityQueue queue;
    @Mock private DecisionService decisionService;

    private static ScoredTransactionEvent event(String transactionId) {
        return new ScoredTransactionEvent(
                transactionId, "sender-1", "receiver-1", new BigDecimal("500.00"), "USD",
                "device-1", "10.0.0.1", Instant.now(), 3, new BigDecimal("50.00"),
                Set.of("device-1"), Set.of("10.0.0.1"),
                new BigDecimal("0.60"), "explanation", ScoringMethod.AI);
    }

    @Test
    void processNextDecidesTheDequeuedEventAndReturnsTrue() throws InterruptedException {
        DecisionWorker worker = new DecisionWorker(queue, decisionService);
        ScoredTransactionEvent event = event("tx-1");
        when(queue.blockingDequeueHighestPriority()).thenReturn(event);

        boolean continued = worker.processNext();

        assertThat(continued).isTrue();
        verify(decisionService).decideAndPublish(event);
    }

    @Test
    void processNextReturnsFalseWhenInterruptedWhileWaitingForWork() throws InterruptedException {
        DecisionWorker worker = new DecisionWorker(queue, decisionService);
        when(queue.blockingDequeueHighestPriority()).thenThrow(new InterruptedException());

        boolean continued = worker.processNext();

        assertThat(continued).isFalse();
        assertThat(Thread.interrupted()).isTrue(); // also clears the flag for subsequent tests
    }

    @Test
    void processNextSwallowsExceptionsFromDecidingSoTheLoopKeepsDraining() throws InterruptedException {
        DecisionWorker worker = new DecisionWorker(queue, decisionService);
        ScoredTransactionEvent event = event("tx-2");
        when(queue.blockingDequeueHighestPriority()).thenReturn(event);
        doThrow(new RuntimeException("unexpected failure")).when(decisionService).decideAndPublish(any());

        boolean continued = worker.processNext();

        assertThat(continued).isTrue();
    }

    @Test
    void stopBlocksUntilTheInFlightEventFinishesProcessing() throws Exception {
        // Uses a real queue (not a mock) so the background thread started by start() genuinely
        // dequeues and processes an event, proving stop() doesn't return while decideAndPublish
        // is still running on the worker thread — see DecisionWorker.stop()'s javadoc for why
        // that matters (Spring would otherwise close the DataSource/KafkaTemplate underneath it).
        DecisionPriorityQueue realQueue = new DecisionPriorityQueue(10);
        AtomicBoolean finishedDeciding = new AtomicBoolean(false);
        doAnswer(invocation -> {
            // stop() interrupts this thread via shutdownNow(); a plain Thread.sleep() would abort
            // immediately on that interrupt, which would NOT reproduce the bug this test guards
            // against — real in-flight work here is a blocking JDBC save + an async Kafka send,
            // neither of which aborts on thread interruption, so this loop swallows the interrupt
            // and keeps "working" for the full duration to simulate that faithfully.
            long deadline = System.nanoTime() + Duration.ofMillis(300).toNanos();
            while (System.nanoTime() < deadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    // swallowed deliberately, see comment above
                }
            }
            finishedDeciding.set(true);
            return null;
        }).when(decisionService).decideAndPublish(any());
        DecisionWorker worker = new DecisionWorker(realQueue, decisionService);

        worker.start();
        realQueue.enqueue(event("tx-1"));
        Thread.sleep(50); // let the worker thread dequeue and enter the slow decideAndPublish call

        worker.stop();

        assertThat(finishedDeciding.get()).isTrue();
    }
}
