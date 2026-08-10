package com.ringwatch.reconciliation.correlation;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FR24: correlates an in-flight reconciliation request to its original decision, so the eventual
 * {@code ReconciliationDecisionResult} can be diffed against it. Deliberately in-memory only,
 * mirroring decision-engine's {@code RingMembershipRegistry}'s own documented in-memory-only
 * precedent: a restart mid-run just drops whatever samples were in flight, which get silently
 * re-sampled on the next scheduled run - an acceptable tradeoff for a periodic, best-effort
 * diagnostic job with no persistence of its own.
 *
 * <p>An entry is normally removed within seconds by {@code ReconciliationResultListener} once the
 * round trip completes. If a hop fails silently instead (e.g. the request never reaches
 * ai-risk-scoring-service/decision-engine, or their response never arrives), nothing else removes
 * it - without a sweep this map would grow unbounded over the service's uptime. The periodic
 * cleanup below evicts anything that's been pending longer than a full round trip should ever
 * take.
 */
@Component
public class CorrelationStore {

    private static final Logger log = LoggerFactory.getLogger(CorrelationStore.class);
    private static final Duration MAX_PENDING_AGE = Duration.ofMinutes(10);

    private final Map<String, PendingReconciliation> pendingByCorrelationId = new ConcurrentHashMap<>();

    public void put(String correlationId, PendingReconciliation pending) {
        pendingByCorrelationId.put(correlationId, pending);
    }

    /** Returns {@code null} if absent (e.g. a restart lost this run's in-flight state). */
    public PendingReconciliation remove(String correlationId) {
        return pendingByCorrelationId.remove(correlationId);
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    void evictStaleEntries() {
        Instant cutoff = Instant.now().minus(MAX_PENDING_AGE);
        int before = pendingByCorrelationId.size();
        pendingByCorrelationId.values().removeIf(pending -> pending.sampledAt().isBefore(cutoff));
        int removed = before - pendingByCorrelationId.size();
        if (removed > 0) {
            log.warn("Evicted {} reconciliation correlation(s) that never received a response within {}",
                    removed, MAX_PENDING_AGE);
        }
    }
}
