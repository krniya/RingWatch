package com.ringwatch.decision.kafka;

import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.decision.priority.DecisionPriorityQueue;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code transactions.scored} and hands each event to {@link DecisionPriorityQueue}
 * rather than deciding it inline — {@link DecisionWorker} does the actual deciding, draining the
 * queue highest-risk-first (FR14). See {@link DecisionPriorityQueue}'s javadoc for the
 * ack-before-processing durability tradeoff this decoupling makes.
 */
@Component
public class DecisionListener {

    private final DecisionPriorityQueue queue;

    public DecisionListener(DecisionPriorityQueue queue) {
        this.queue = queue;
    }

    @KafkaListener(topics = Topics.TRANSACTIONS_SCORED)
    public void onTransactionScored(ScoredTransactionEvent event) throws InterruptedException {
        queue.enqueue(event);
    }
}
