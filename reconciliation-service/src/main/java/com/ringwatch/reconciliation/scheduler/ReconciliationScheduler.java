package com.ringwatch.reconciliation.scheduler;

import com.ringwatch.reconciliation.audit.AuditServiceClient;
import com.ringwatch.reconciliation.audit.dto.AuditLogEntryDto;
import com.ringwatch.reconciliation.kafka.ReconciliationRequestProducer;
import com.ringwatch.reconciliation.sampling.TransactionSampler;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** FR24: the periodic trigger that samples past decisions and kicks off their re-scoring. */
@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final AuditServiceClient auditServiceClient;
    private final ReconciliationRequestProducer requestProducer;
    private final long minAgeMs;
    private final long maxAgeMs;
    private final int sampleSize;

    public ReconciliationScheduler(
            AuditServiceClient auditServiceClient,
            ReconciliationRequestProducer requestProducer,
            @Value("${ringwatch.reconciliation.lookback.min-age-ms}") long minAgeMs,
            @Value("${ringwatch.reconciliation.lookback.max-age-ms}") long maxAgeMs,
            @Value("${ringwatch.reconciliation.sample-size}") int sampleSize) {
        this.auditServiceClient = auditServiceClient;
        this.requestProducer = requestProducer;
        this.minAgeMs = minAgeMs;
        this.maxAgeMs = maxAgeMs;
        this.sampleSize = sampleSize;
    }

    @Scheduled(fixedDelayString = "${ringwatch.reconciliation.schedule.interval-ms}")
    public void runReconciliation() {
        Instant now = Instant.now();
        Instant from = now.minusMillis(maxAgeMs);
        Instant to = now.minusMillis(minAgeMs);

        try {
            List<AuditLogEntryDto> entries = auditServiceClient.fetchAuditEntries(from, to);
            List<AuditLogEntryDto> sampled = TransactionSampler.sample(entries, sampleSize);

            log.info("Reconciliation run: sampled {} of {} decided transactions between {} and {}",
                    sampled.size(), entries.size(), from, to);

            for (AuditLogEntryDto entry : sampled) {
                // Isolated per entry: a malformed/partial payload on one sampled transaction (e.g.
                // an older row predating a DecisionEvent schema change) must not cost the rest of
                // this run its coverage.
                try {
                    requestProducer.requestReconciliation(entry);
                } catch (Exception e) {
                    log.error("Failed to request reconciliation for transaction '{}'; skipping it this run",
                            entry.transactionId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Reconciliation run failed; will retry on the next scheduled run", e);
        }
    }
}
