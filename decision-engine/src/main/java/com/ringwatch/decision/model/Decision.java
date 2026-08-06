package com.ringwatch.decision.model;

import com.ringwatch.common.event.DecisionOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "decisions")
@Getter
@NoArgsConstructor
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DecisionOutcome outcome;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column
    private String overriddenBy;

    @Column(columnDefinition = "TEXT")
    private String overrideReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Decision(String transactionId, DecisionOutcome outcome, String reason) {
        this.transactionId = transactionId;
        this.outcome = outcome;
        this.reason = reason;
    }
}
