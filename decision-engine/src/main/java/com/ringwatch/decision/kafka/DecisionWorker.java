package com.ringwatch.decision.kafka;

import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.decision.priority.DecisionPriorityQueue;
import com.ringwatch.decision.service.DecisionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Background thread that continuously drains {@link DecisionPriorityQueue} highest-risk-first
 * and decides each event. Runs on its own single thread, separate from the Kafka listener
 * container thread, so that draining in priority order (FR14) doesn't depend on how Spring Kafka
 * schedules listener invocations.
 *
 * <p>{@link #processNext()} catches broadly rather than letting any single event's failure kill
 * the loop: this worker is the only thing draining the queue, so if it stopped on the first
 * unexpected exception, every transaction already enqueued (and every one enqueued afterward,
 * once the bounded queue fills) would silently stop being decided — the same "the safety net
 * itself must not break" reasoning already applied to {@code RuleBasedRiskScorer} in
 * ai-risk-scoring-service.
 */
@Component
public class DecisionWorker {

    private static final Logger log = LoggerFactory.getLogger(DecisionWorker.class);

    private final DecisionPriorityQueue queue;
    private final DecisionService decisionService;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "decision-worker"));
    private volatile boolean running = true;

    public DecisionWorker(DecisionPriorityQueue queue, DecisionService decisionService) {
        this.queue = queue;
        this.decisionService = decisionService;
    }

    @PostConstruct
    void start() {
        executor.submit(this::runLoop);
    }

    private void runLoop() {
        while (running) {
            if (!processNext()) {
                break;
            }
        }
    }

    /**
     * Processes exactly one queued event. Returns {@code false} only when the thread was
     * interrupted (i.e. on shutdown), so {@link #runLoop()} knows to stop; any other failure is
     * logged and swallowed so draining continues.
     */
    boolean processNext() {
        ScoredTransactionEvent event;
        try {
            event = queue.blockingDequeueHighestPriority();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            decisionService.decideAndPublish(event);
        } catch (Exception e) {
            log.error("Failed to decide transaction '{}'; continuing to drain the queue.",
                    event.transactionId(), e);
        }
        return true;
    }

    /**
     * Blocks until the worker thread has actually exited, not just been asked to. Spring destroys
     * beans in dependency order, so without waiting here, {@code shutdownNow()} would return
     * immediately and Spring could proceed to close {@code DecisionService}'s DataSource/
     * KafkaTemplate while the worker thread was still mid-{@code decideAndPublish} for an
     * already-dequeued event — silently dropping a decision whose Kafka offset was already
     * committed at enqueue time, on every ordinary restart under load, not just on a crash.
     */
    @PreDestroy
    void stop() throws InterruptedException {
        running = false;
        executor.shutdownNow();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            log.warn("Decision worker did not stop within the shutdown timeout.");
        }
    }
}
