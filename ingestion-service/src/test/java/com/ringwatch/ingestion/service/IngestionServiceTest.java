package com.ringwatch.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ringwatch.common.event.TransactionRawEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.ingestion.controller.dto.CreateTransactionRequest;
import com.ringwatch.ingestion.model.Transaction;
import com.ringwatch.ingestion.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private KafkaTemplate<String, TransactionRawEvent> kafkaTemplate;

    private IngestionService ingestionService;

    private static CreateTransactionRequest sampleRequest(String transactionId) {
        return new CreateTransactionRequest(
                transactionId, "sender-1", "receiver-1",
                new BigDecimal("100.00"), "USD", "device-1", "127.0.0.1", Instant.now());
    }

    @Test
    void submitPersistsAndPublishesNewTransaction() {
        ingestionService = new IngestionService(transactionRepository, kafkaTemplate);
        when(transactionRepository.findByTransactionId("tx-1")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        CreateTransactionRequest request = sampleRequest("tx-1");
        IngestionResult result = ingestionService.submit(request);

        assertThat(result.duplicate()).isFalse();
        assertThat(result.transaction().getTransactionId()).isEqualTo("tx-1");

        ArgumentCaptor<TransactionRawEvent> eventCaptor = ArgumentCaptor.forClass(TransactionRawEvent.class);
        verify(kafkaTemplate).send(eq(Topics.TRANSACTIONS_RAW), eq("tx-1"), eventCaptor.capture());
        TransactionRawEvent published = eventCaptor.getValue();
        assertThat(published.transactionId()).isEqualTo("tx-1");
        assertThat(published.senderAccountId()).isEqualTo(request.senderAccountId());
        assertThat(published.receiverAccountId()).isEqualTo(request.receiverAccountId());
        assertThat(published.amount()).isEqualByComparingTo(request.amount());
        assertThat(published.currency()).isEqualTo(request.currency());
        assertThat(published.deviceId()).isEqualTo(request.deviceId());
        assertThat(published.ipAddress()).isEqualTo(request.ipAddress());
        assertThat(published.timestamp()).isEqualTo(request.timestamp());
    }

    @Test
    void submitLogsWithoutThrowingWhenKafkaPublishFails() {
        ingestionService = new IngestionService(transactionRepository, kafkaTemplate);
        when(transactionRepository.findByTransactionId("tx-fail")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        CompletableFuture<SendResult<String, TransactionRawEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedFuture);

        IngestionResult result = ingestionService.submit(sampleRequest("tx-fail"));

        assertThat(result.duplicate()).isFalse();
    }

    @Test
    void submitReturnsExistingWithoutPublishingForKnownTransactionId() {
        ingestionService = new IngestionService(transactionRepository, kafkaTemplate);
        Transaction existing = new Transaction(
                "tx-2", "sender-1", "receiver-1",
                new BigDecimal("50.00"), "USD", "device-1", "127.0.0.1", Instant.now());
        when(transactionRepository.findByTransactionId("tx-2")).thenReturn(Optional.of(existing));

        IngestionResult result = ingestionService.submit(sampleRequest("tx-2"));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.transaction()).isSameAs(existing);
        verify(transactionRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void submitReturnsOriginalWhenResubmittedTransactionIdHasDifferentPayload() {
        ingestionService = new IngestionService(transactionRepository, kafkaTemplate);
        Transaction existing = new Transaction(
                "tx-mismatch", "sender-1", "receiver-1",
                new BigDecimal("50.00"), "USD", "device-1", "127.0.0.1", Instant.now());
        when(transactionRepository.findByTransactionId("tx-mismatch")).thenReturn(Optional.of(existing));
        CreateTransactionRequest mismatched = new CreateTransactionRequest(
                "tx-mismatch", "sender-1", "receiver-1",
                new BigDecimal("999999.00"), "USD", "device-1", "127.0.0.1", Instant.now());

        IngestionResult result = ingestionService.submit(mismatched);

        assertThat(result.duplicate()).isTrue();
        assertThat(result.transaction()).isSameAs(existing);
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void submitTreatsUniqueConstraintRaceAsDuplicate() {
        ingestionService = new IngestionService(transactionRepository, kafkaTemplate);
        Transaction winner = new Transaction(
                "tx-3", "sender-1", "receiver-1",
                new BigDecimal("75.00"), "USD", "device-1", "127.0.0.1", Instant.now());
        when(transactionRepository.findByTransactionId("tx-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(transactionRepository.save(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        IngestionResult result = ingestionService.submit(sampleRequest("tx-3"));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.transaction()).isSameAs(winner);
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
