package com.ringwatch.fraudring.service;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.event.FraudRingEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.fraudring.graph.AccountClusterGraph;
import com.ringwatch.fraudring.graph.TransactionGraph;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Orchestrates FR9-FR12 per enriched transaction: records the transaction against both the
 * account-cluster graph (FR9/FR10) and the directed transfer graph (FR11), and publishes a
 * {@link FraudRingEvent} for either signal that fires. A single transaction can trigger both
 * (e.g. closing a cycle among accounts that also just crossed the cluster-size threshold),
 * neither, or one.
 */
@Service
public class FraudRingDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudRingDetectionService.class);

    private final AccountClusterGraph accountClusterGraph;
    private final TransactionGraph transactionGraph;
    private final RingExplainer ringExplainer;
    private final KafkaTemplate<String, FraudRingEvent> kafkaTemplate;

    public FraudRingDetectionService(
            AccountClusterGraph accountClusterGraph,
            TransactionGraph transactionGraph,
            RingExplainer ringExplainer,
            KafkaTemplate<String, FraudRingEvent> kafkaTemplate) {
        this.accountClusterGraph = accountClusterGraph;
        this.transactionGraph = transactionGraph;
        this.ringExplainer = ringExplainer;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void process(EnrichedTransactionEvent event) {
        accountClusterGraph.observe(event).ifPresent(update -> publishRing(
                update.memberAccountIds(),
                "%d accounts are connected via shared device/IP usage or fund transfers (most recently: device '%s', IP '%s')"
                        .formatted(update.memberAccountIds().size(), update.triggeringDeviceId(), update.triggeringIpAddress())));

        transactionGraph.recordTransferAndDetectCycle(event.senderAccountId(), event.receiverAccountId())
                .ifPresent(cycle -> publishRing(
                        Set.copyOf(cycle),
                        "Circular fund movement detected: " + String.join(" -> ", cycle)));
    }

    private void publishRing(Set<String> memberAccountIds, String triggerDescription) {
        String explanation = ringExplainer.explain(new RingContext(memberAccountIds, triggerDescription));
        FraudRingEvent ringEvent = new FraudRingEvent(
                UUID.randomUUID().toString(), memberAccountIds, triggerDescription, explanation, Instant.now());
        kafkaTemplate.send(Topics.TRANSACTIONS_RING_FLAGGED, ringEvent.ringId(), ringEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish fraud ring event '{}' to {}", ringEvent.ringId(),
                                Topics.TRANSACTIONS_RING_FLAGGED, ex);
                    }
                });
    }
}
