package com.ringwatch.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ThrottleHeapTest {

    @Test
    void observingBelowCapacityInsertsEachKey() {
        ThrottleHeap heap = new ThrottleHeap(3);

        heap.observe("a", 1);
        heap.observe("b", 2);

        assertThat(heap.topByCountDescending())
                .extracting(ThrottleCount::key)
                .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void reobservingAnAlreadyTrackedKeyUpdatesItsCountInPlaceRatherThanDuplicatingIt() {
        ThrottleHeap heap = new ThrottleHeap(3);

        heap.observe("a", 1);
        heap.observe("a", 2);
        heap.observe("a", 3);

        List<ThrottleCount> top = heap.topByCountDescending();
        assertThat(top).hasSize(1);
        assertThat(top.get(0)).isEqualTo(new ThrottleCount("a", 3));
    }

    @Test
    void topByCountDescendingOrdersHighestFirst() {
        ThrottleHeap heap = new ThrottleHeap(3);

        heap.observe("low", 1);
        heap.observe("high", 10);
        heap.observe("mid", 5);

        assertThat(heap.topByCountDescending())
                .extracting(ThrottleCount::key)
                .containsExactly("high", "mid", "low");
    }

    @Test
    void aNewKeyIsIgnoredOnceTheHeapIsFullAndItsCumulativeCountDoesNotExceedTheMinimum() {
        ThrottleHeap heap = new ThrottleHeap(2);
        heap.observe("a", 5);
        heap.observe("b", 5);

        heap.observe("c", 3);

        assertThat(heap.topByCountDescending())
                .extracting(ThrottleCount::key)
                .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void aNewKeyDisplacesTheCurrentMinimumOnceItsCumulativeCountExceedsIt() {
        // This is the case the naive "bounded heap alone" design gets wrong: a brand-new key
        // (count 1 on its first throttle) could never beat an established minimum directly, but
        // its caller-tracked cumulative count can - once it does, it must be able to break in.
        ThrottleHeap heap = new ThrottleHeap(2);
        heap.observe("a", 5);
        heap.observe("b", 3);

        heap.observe("c", 10);

        assertThat(heap.topByCountDescending())
                .extracting(ThrottleCount::key)
                .containsExactlyInAnyOrder("a", "c");
    }

    @Test
    void aZeroCapacityHeapIgnoresEveryObservationWithoutThrowing() {
        ThrottleHeap heap = new ThrottleHeap(0);

        heap.observe("a", 1);

        assertThat(heap.topByCountDescending()).isEmpty();
    }

    @Test
    void evictionAtCapacityOneReplacesTheSoleEntry() {
        ThrottleHeap heap = new ThrottleHeap(1);
        heap.observe("a", 5);

        heap.observe("b", 10);

        assertThat(heap.topByCountDescending()).containsExactly(new ThrottleCount("b", 10));
    }
}
