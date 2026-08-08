package com.ringwatch.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One immutable row per (transactionId, eventType) — no updates, no deletes. The unique
 * constraint on that pair is this entity's idempotency guard against Kafka redelivery, mirroring
 * the dedup pattern already used by {@code IngestionService}/{@code DecisionService}: a
 * transaction legitimately gets multiple rows here (one per lifecycle event), but never two rows
 * for the *same* event.
 */
@Entity
@Table(name = "audit_logs", uniqueConstraints = @UniqueConstraint(columnNames = {"transactionId", "eventType"}))
@Getter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditEventType eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Populated only for {@link AuditEventType#OVERRIDDEN} events, from the analyst's JWT identity. */
    @Column
    private String userId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    public AuditLog(String transactionId, AuditEventType eventType, String payload, String userId) {
        this.transactionId = transactionId;
        this.eventType = eventType;
        this.payload = payload;
        this.userId = userId;
    }
}
