package com.ringwatch.enrichment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.event.TransactionRawEvent;
import com.ringwatch.common.kafka.Topics;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class EnrichmentServiceTest {

    @Mock private KafkaTemplate<String, EnrichedTransactionEvent> kafkaTemplate;

    private EnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new EnrichmentService(10, kafkaTemplate);
    }

    private static TransactionRawEvent event(
            String transactionId, String accountId, String device, String ip, String amount) {
        return new TransactionRawEvent(
                transactionId, accountId, "receiver-1",
                new BigDecimal(amount), "USD", device, ip, Instant.now());
    }

    @Test
    void enrichReflectsEmptyHistoryForFirstTransactionFromAccount() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        EnrichedTransactionEvent enriched = service.enrich(event("tx-1", "acct-1", "device-1", "10.0.0.1", "100.00"));

        assertThat(enriched.recentTxnCount()).isZero();
        assertThat(enriched.avgTxnAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(enriched.knownDevices()).isEmpty();
        assertThat(enriched.knownIps()).isEmpty();

        ArgumentCaptor<EnrichedTransactionEvent> captor = ArgumentCaptor.forClass(EnrichedTransactionEvent.class);
        verify(kafkaTemplate).send(eq(Topics.TRANSACTIONS_ENRICHED), eq("acct-1"), captor.capture());
        assertThat(captor.getValue().transactionId()).isEqualTo("tx-1");
    }

    @Test
    void secondTransactionFromSameAccountReflectsFirstAsHistoryNotIncludingItself() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.enrich(event("tx-1", "acct-1", "device-0", "10.0.0.0", "300.00"));
        EnrichedTransactionEvent second =
                service.enrich(event("tx-2", "acct-1", "device-1", "10.0.0.1", "50.00"));

        assertThat(second.recentTxnCount()).isEqualTo(1);
        assertThat(second.avgTxnAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(second.knownDevices()).containsExactly("device-0");
        assertThat(second.knownIps()).containsExactly("10.0.0.0");
    }

    @Test
    void thirdTransactionReflectsBothPriorTransactionsAccumulated() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.enrich(event("tx-1", "acct-1", "device-0", "10.0.0.0", "300.00"));
        service.enrich(event("tx-2", "acct-1", "device-1", "10.0.0.1", "50.00"));
        EnrichedTransactionEvent third =
                service.enrich(event("tx-3", "acct-1", "device-2", "10.0.0.2", "10.00"));

        assertThat(third.recentTxnCount()).isEqualTo(2);
        assertThat(third.avgTxnAmount()).isEqualByComparingTo(new BigDecimal("175.00"));
        assertThat(third.knownDevices()).containsExactlyInAnyOrder("device-0", "device-1");
        assertThat(third.knownIps()).containsExactlyInAnyOrder("10.0.0.0", "10.0.0.1");
    }

    @Test
    void enrichDoesNotThrowWhenKafkaPublishFails() {
        CompletableFuture<SendResult<String, EnrichedTransactionEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failed);

        EnrichedTransactionEvent enriched = service.enrich(event("tx-1", "acct-1", "device-1", "10.0.0.1", "100.00"));

        assertThat(enriched.transactionId()).isEqualTo("tx-1");
    }

    @Test
    void differentAccountsTrackedIndependently() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.enrich(event("tx-1", "acct-a", "device-1", "10.0.0.1", "100.00"));
        EnrichedTransactionEvent enrichedB =
                service.enrich(event("tx-2", "acct-b", "device-1", "10.0.0.1", "100.00"));

        assertThat(enrichedB.recentTxnCount()).isZero();
    }
}
