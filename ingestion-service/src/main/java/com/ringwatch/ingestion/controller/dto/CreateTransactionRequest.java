package com.ringwatch.ingestion.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;

public record CreateTransactionRequest(
        @NotBlank String transactionId,
        @NotBlank String senderAccountId,
        @NotBlank String receiverAccountId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 currency code") String currency,
        @NotBlank String deviceId,
        @NotBlank String ipAddress,
        @NotNull Instant timestamp
) {
}
