package com.ringwatch.reconciliation.sampling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.NullNode;
import com.ringwatch.reconciliation.audit.dto.AuditLogEntryDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionSamplerTest {

    private static AuditLogEntryDto entry(String transactionId, String eventType) {
        return new AuditLogEntryDto(UUID.randomUUID(), transactionId, eventType, NullNode.getInstance(), null, Instant.now());
    }

    @Test
    void filtersOutNonDecidedEntries() {
        List<AuditLogEntryDto> entries = List.of(
                entry("tx-1", "CREATED"), entry("tx-1", "SCORED"), entry("tx-1", "DECIDED"), entry("tx-2", "OVERRIDDEN"));

        List<AuditLogEntryDto> sampled = TransactionSampler.sample(entries, 10);

        assertThat(sampled).extracting(AuditLogEntryDto::transactionId).containsExactly("tx-1");
    }

    @Test
    void boundsTheSampleToTheRequestedSize() {
        List<AuditLogEntryDto> entries = List.of(
                entry("tx-1", "DECIDED"), entry("tx-2", "DECIDED"), entry("tx-3", "DECIDED"));

        List<AuditLogEntryDto> sampled = TransactionSampler.sample(entries, 2);

        assertThat(sampled).hasSize(2);
    }

    @Test
    void returnsEmptyWhenNoDecidedEntriesExist() {
        List<AuditLogEntryDto> entries = List.of(entry("tx-1", "CREATED"), entry("tx-1", "SCORED"));

        assertThat(TransactionSampler.sample(entries, 10)).isEmpty();
    }

    @Test
    void excludesTransactionsAlreadyReconciledInTheSameWindow() {
        List<AuditLogEntryDto> entries = List.of(
                entry("tx-1", "DECIDED"), entry("tx-1", "RECONCILED"),
                entry("tx-2", "DECIDED"));

        List<AuditLogEntryDto> sampled = TransactionSampler.sample(entries, 10);

        assertThat(sampled).extracting(AuditLogEntryDto::transactionId).containsExactly("tx-2");
    }
}
