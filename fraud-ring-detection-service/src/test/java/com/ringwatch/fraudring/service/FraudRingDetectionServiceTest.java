package com.ringwatch.fraudring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.event.FraudRingEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.fraudring.graph.AccountClusterGraph;
import com.ringwatch.fraudring.graph.ClusterUpdate;
import com.ringwatch.fraudring.graph.TransactionGraph;
import com.ringwatch.fraudring.model.FraudRingDetection;
import com.ringwatch.fraudring.repository.FraudRingDetectionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class FraudRingDetectionServiceTest {

    @Mock private AccountClusterGraph accountClusterGraph;
    @Mock private TransactionGraph transactionGraph;
    @Mock private RingExplainer ringExplainer;
    @Mock private KafkaTemplate<String, FraudRingEvent> kafkaTemplate;
    @Mock private FraudRingDetectionRepository fraudRingDetectionRepository;

    private FraudRingDetectionService service;

    private static EnrichedTransactionEvent event() {
        return new EnrichedTransactionEvent(
                "tx-1", "A", "B", new BigDecimal("100.00"), "USD",
                "dev-1", "10.0.0.1", Instant.now(), 1, BigDecimal.TEN, Set.of(), Set.of());
    }

    @Test
    void clusterUpdateAlonePublishesOneRingEvent() {
        service = new FraudRingDetectionService(
                accountClusterGraph, transactionGraph, ringExplainer, kafkaTemplate, fraudRingDetectionRepository);
        when(accountClusterGraph.observe(any())).thenReturn(
                Optional.of(new ClusterUpdate(Set.of("A", "B", "C"), "dev-1", "10.0.0.1")));
        when(transactionGraph.recordTransferAndDetectCycle(any(), any())).thenReturn(Optional.empty());
        when(ringExplainer.explain(any())).thenReturn("explanation");
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.process(event());

        ArgumentCaptor<FraudRingEvent> captor = ArgumentCaptor.forClass(FraudRingEvent.class);
        verify(kafkaTemplate, times(1)).send(eq(Topics.TRANSACTIONS_RING_FLAGGED), any(), captor.capture());
        assertThat(captor.getValue().memberAccountIds()).containsExactlyInAnyOrder("A", "B", "C");
        assertThat(captor.getValue().aiExplanation()).isEqualTo("explanation");

        ArgumentCaptor<FraudRingDetection> savedCaptor = ArgumentCaptor.forClass(FraudRingDetection.class);
        verify(fraudRingDetectionRepository, times(1)).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getMemberAccountIds()).containsExactlyInAnyOrder("A", "B", "C");
        assertThat(savedCaptor.getValue().getAiExplanation()).isEqualTo("explanation");
    }

    @Test
    void cycleDetectionAlonePublishesOneRingEvent() {
        service = new FraudRingDetectionService(
                accountClusterGraph, transactionGraph, ringExplainer, kafkaTemplate, fraudRingDetectionRepository);
        when(accountClusterGraph.observe(any())).thenReturn(Optional.empty());
        when(transactionGraph.recordTransferAndDetectCycle(any(), any()))
                .thenReturn(Optional.of(List.of("A", "B", "C", "A")));
        when(ringExplainer.explain(any())).thenReturn("explanation");
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.process(event());

        ArgumentCaptor<FraudRingEvent> captor = ArgumentCaptor.forClass(FraudRingEvent.class);
        verify(kafkaTemplate, times(1)).send(eq(Topics.TRANSACTIONS_RING_FLAGGED), any(), captor.capture());
        assertThat(captor.getValue().memberAccountIds()).containsExactlyInAnyOrder("A", "B", "C");
        assertThat(captor.getValue().sharedAttributes()).contains("Circular fund movement");

        ArgumentCaptor<FraudRingDetection> savedCaptor = ArgumentCaptor.forClass(FraudRingDetection.class);
        verify(fraudRingDetectionRepository, times(1)).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getSharedAttributes()).contains("Circular fund movement");
    }

    @Test
    void bothSignalsFiringPublishesTwoRingEvents() {
        service = new FraudRingDetectionService(
                accountClusterGraph, transactionGraph, ringExplainer, kafkaTemplate, fraudRingDetectionRepository);
        when(accountClusterGraph.observe(any())).thenReturn(
                Optional.of(new ClusterUpdate(Set.of("A", "B", "C"), "dev-1", "10.0.0.1")));
        when(transactionGraph.recordTransferAndDetectCycle(any(), any()))
                .thenReturn(Optional.of(List.of("A", "B", "A")));
        when(ringExplainer.explain(any())).thenReturn("explanation");
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.process(event());

        verify(kafkaTemplate, times(2)).send(eq(Topics.TRANSACTIONS_RING_FLAGGED), any(), any());
        verify(fraudRingDetectionRepository, times(2)).save(any());
    }

    @Test
    void neitherSignalFiringPublishesNothing() {
        service = new FraudRingDetectionService(
                accountClusterGraph, transactionGraph, ringExplainer, kafkaTemplate, fraudRingDetectionRepository);
        when(accountClusterGraph.observe(any())).thenReturn(Optional.empty());
        when(transactionGraph.recordTransferAndDetectCycle(any(), any())).thenReturn(Optional.empty());

        service.process(event());

        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(ringExplainer, never()).explain(any());
        verify(fraudRingDetectionRepository, never()).save(any());
    }
}
