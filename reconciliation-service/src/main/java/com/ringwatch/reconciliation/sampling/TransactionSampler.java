package com.ringwatch.reconciliation.sampling;

import com.ringwatch.reconciliation.audit.dto.AuditLogEntryDto;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FR24: picks which past decisions to re-check. audit-service's {@code GET /audit} has no
 * eventType filter (adding one would mean casting an enum-typed bind parameter through a
 * documented-fragile Postgres prepared-statement pattern on an already-shipped endpoint - not
 * worth the risk for this one new caller), so filtering to DECIDED entries happens here instead,
 * over the already-bounded (small time-window) result set.
 *
 * <p>Entries that already have a RECONCILED row in the same fetched window are excluded: without
 * this, a deterministic "oldest N" sample re-selects the same transactions on every scheduled run
 * for their entire time in the lookback window - wasting a real LLM call on each re-check (only
 * the first ever gets recorded, since {@code AuditLog}'s unique constraint silently drops the
 * rest) while the remaining decided transactions in the window never get sampled at all.
 */
public final class TransactionSampler {

    private TransactionSampler() {
    }

    public static List<AuditLogEntryDto> sample(List<AuditLogEntryDto> entries, int sampleSize) {
        Set<String> alreadyReconciled = new HashSet<>();
        for (AuditLogEntryDto entry : entries) {
            if ("RECONCILED".equals(entry.eventType())) {
                alreadyReconciled.add(entry.transactionId());
            }
        }

        List<AuditLogEntryDto> candidates = new ArrayList<>();
        for (AuditLogEntryDto entry : entries) {
            if ("DECIDED".equals(entry.eventType()) && !alreadyReconciled.contains(entry.transactionId())) {
                candidates.add(entry);
            }
        }
        return candidates.size() <= sampleSize ? candidates : candidates.subList(0, sampleSize);
    }
}
