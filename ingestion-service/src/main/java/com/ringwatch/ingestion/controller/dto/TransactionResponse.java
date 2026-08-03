package com.ringwatch.ingestion.controller.dto;

import com.ringwatch.ingestion.model.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String transactionId,
        String senderAccountId,
        String receiverAccountId,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        boolean duplicate
) {
    public static TransactionResponse from(Transaction transaction, boolean duplicate) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionId(),
                transaction.getSenderAccountId(),
                transaction.getReceiverAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getOccurredAt(),
                duplicate);
    }
}
