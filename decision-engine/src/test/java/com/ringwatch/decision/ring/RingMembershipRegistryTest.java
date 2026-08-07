package com.ringwatch.decision.ring;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringwatch.common.event.FraudRingEvent;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RingMembershipRegistryTest {

    private final RingMembershipRegistry registry = new RingMembershipRegistry();

    private static FraudRingEvent ringEvent(String ringId, String... memberAccountIds) {
        return new FraudRingEvent(ringId, Set.of(memberAccountIds), "shared device", "explanation", Instant.now());
    }

    @Test
    void unknownAccountHasNoMembership() {
        assertThat(registry.membershipOf("acct-1")).isEmpty();
    }

    @Test
    void everyMemberOfARingIsRecorded() {
        registry.recordRing(ringEvent("ring-1", "A", "B", "C"));

        assertThat(registry.membershipOf("A")).isPresent();
        assertThat(registry.membershipOf("B")).isPresent();
        assertThat(registry.membershipOf("C")).isPresent();
        assertThat(registry.membershipOf("A").get().ringId()).isEqualTo("ring-1");
    }

    @Test
    void accountNotInTheRingStaysUnrecorded() {
        registry.recordRing(ringEvent("ring-1", "A", "B"));

        assertThat(registry.membershipOf("Z")).isEmpty();
    }

    @Test
    void aLaterRingEventForTheSameAccountOverwritesTheEarlierOne() {
        registry.recordRing(ringEvent("ring-1", "A"));
        registry.recordRing(ringEvent("ring-2", "A"));

        assertThat(registry.membershipOf("A").get().ringId()).isEqualTo("ring-2");
    }
}
