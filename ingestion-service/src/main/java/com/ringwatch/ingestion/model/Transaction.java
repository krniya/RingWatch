package com.ringwatch.ingestion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false)
    private String senderAccountId;

    @Column(nullable = false)
    private String receiverAccountId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private Instant occurredAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Transaction(
            String transactionId,
            String senderAccountId,
            String receiverAccountId,
            BigDecimal amount,
            String currency,
            String deviceId,
            String ipAddress,
            Instant occurredAt) {
        this.transactionId = transactionId;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.amount = amount;
        this.currency = currency;
        this.deviceId = deviceId;
        this.ipAddress = ipAddress;
        this.occurredAt = occurredAt;
    }
}
