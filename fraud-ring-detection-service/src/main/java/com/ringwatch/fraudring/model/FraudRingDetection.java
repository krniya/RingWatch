package com.ringwatch.fraudring.model;

import com.ringwatch.common.event.FraudRingEvent;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * An immutable record of one {@link FraudRingEvent} publication (FR23). {@code ringId} is a fresh
 * UUID on every publish - a growing cluster produces several rows, not one row that gets updated -
 * mirroring {@code audit_logs}' append-only philosophy rather than trying to maintain a single
 * durable "ring" identity across republications.
 */
@Entity
@Table(name = "fraud_ring_detections")
@Getter
@NoArgsConstructor
public class FraudRingDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String ringId;

    // EAGER: `open-in-view: false` means the Hibernate session is closed by the time the
    // controller's response gets serialized, and this collection is always needed by callers
    // (there's no read path that only wants the parent row) - small, bounded-size sets (a handful
    // of accounts per ring), so no N+1 concern at this scale.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fraud_ring_detection_members", joinColumns = @JoinColumn(name = "detection_id"))
    @Column(name = "account_id")
    private Set<String> memberAccountIds;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String sharedAttributes;

    @Column(columnDefinition = "TEXT")
    private String aiExplanation;

    @Column(nullable = false)
    private Instant detectedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    public FraudRingDetection(
            String ringId, Set<String> memberAccountIds, String sharedAttributes,
            String aiExplanation, Instant detectedAt) {
        this.ringId = ringId;
        this.memberAccountIds = memberAccountIds;
        this.sharedAttributes = sharedAttributes;
        this.aiExplanation = aiExplanation;
        this.detectedAt = detectedAt;
    }

    public static FraudRingDetection from(FraudRingEvent event) {
        return new FraudRingDetection(
                event.ringId(), event.memberAccountIds(), event.sharedAttributes(),
                event.aiExplanation(), event.detectedAt());
    }
}
