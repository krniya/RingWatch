package com.ringwatch.decision.ring;

import com.ringwatch.common.event.FraudRingEvent;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory index of which accounts currently belong to a known fraud ring, kept up to date by
 * {@link com.ringwatch.decision.kafka.RingFlagListener}. Mirrors the demo-scale in-memory-state
 * approach already used by {@code AccountClusterGraph}/{@code TransactionGraph} in
 * fraud-ring-detection-service - not persisted, so a restart forgets ring membership until the
 * next {@code transactions.ring-flagged} event for that account arrives. A single account can
 * belong to at most one ring here; if a later event re-flags an account under a different ring,
 * the newest ring wins.
 */
@Component
public class RingMembershipRegistry {

    private final Map<String, RingMembership> membershipByAccountId = new ConcurrentHashMap<>();

    public void recordRing(FraudRingEvent event) {
        RingMembership membership = new RingMembership(event.ringId(), event.sharedAttributes());
        for (String accountId : event.memberAccountIds()) {
            membershipByAccountId.put(accountId, membership);
        }
    }

    public Optional<RingMembership> membershipOf(String accountId) {
        return Optional.ofNullable(membershipByAccountId.get(accountId));
    }
}
