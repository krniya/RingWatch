package com.ringwatch.decision.kafka;

import com.ringwatch.common.event.FraudRingEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.decision.ring.RingMembershipRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RingFlagListener {

    private final RingMembershipRegistry registry;

    public RingFlagListener(RingMembershipRegistry registry) {
        this.registry = registry;
    }

    /**
     * The default container factory's {@code spring.json.value.default.type} is fixed to
     * {@code ScoredTransactionEvent} for {@link DecisionListener}; the {@code properties}
     * attribute here overrides just that one property for this listener's consumer, so it still
     * shares the default factory's bootstrap-servers/group-id/{@code ErrorHandlingDeserializer}/
     * trusted-packages configuration rather than duplicating a parallel factory for it.
     */
    @KafkaListener(
            topics = Topics.TRANSACTIONS_RING_FLAGGED,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.FraudRingEvent")
    public void onRingFlagged(FraudRingEvent event) {
        registry.recordRing(event);
    }
}
