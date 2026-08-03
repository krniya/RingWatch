package com.ringwatch.ingestion.service;

import com.ringwatch.common.event.TransactionRawEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.ingestion.controller.dto.CreateTransactionRequest;
import com.ringwatch.ingestion.model.Transaction;
import com.ringwatch.ingestion.repository.TransactionRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, TransactionRawEvent> kafkaTemplate;

    public IngestionService(
            TransactionRepository transactionRepository,
            KafkaTemplate<String, TransactionRawEvent> kafkaTemplate) {
        this.transactionRepository = transactionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public IngestionResult submit(CreateTransactionRequest request) {
        Transaction existing = transactionRepository.findByTransactionId(request.transactionId()).orElse(null);
        if (existing != null) {
            if (!matches(existing, request)) {
                log.warn("Received transactionId '{}' with a payload that differs from the stored "
                                + "transaction; keeping the original and discarding the new payload.",
                        request.transactionId());
            }
            return new IngestionResult(existing, true);
        }

        Transaction transaction = new Transaction(
                request.transactionId(),
                request.senderAccountId(),
                request.receiverAccountId(),
                request.amount(),
                request.currency(),
                request.deviceId(),
                request.ipAddress(),
                request.timestamp());

        Transaction saved;
        try {
            saved = transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException e) {
            return new IngestionResult(
                    transactionRepository.findByTransactionId(request.transactionId())
                            .orElseThrow(() -> e),
                    true);
        }

        kafkaTemplate.send(Topics.TRANSACTIONS_RAW, saved.getTransactionId(), toEvent(saved))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish transaction '{}' to {}", saved.getTransactionId(),
                                Topics.TRANSACTIONS_RAW, ex);
                    }
                });

        return new IngestionResult(saved, false);
    }

    private boolean matches(Transaction existing, CreateTransactionRequest request) {
        return Objects.equals(existing.getSenderAccountId(), request.senderAccountId())
                && Objects.equals(existing.getReceiverAccountId(), request.receiverAccountId())
                && existing.getAmount().compareTo(request.amount()) == 0
                && Objects.equals(existing.getCurrency(), request.currency())
                && Objects.equals(existing.getDeviceId(), request.deviceId())
                && Objects.equals(existing.getIpAddress(), request.ipAddress())
                && Objects.equals(existing.getOccurredAt(), request.timestamp());
    }

    private TransactionRawEvent toEvent(Transaction transaction) {
        return new TransactionRawEvent(
                transaction.getTransactionId(),
                transaction.getSenderAccountId(),
                transaction.getReceiverAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getDeviceId(),
                transaction.getIpAddress(),
                transaction.getOccurredAt());
    }
}
