package com.ringwatch.decision.priority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class MinHeapTest {

    @Test
    void extractMinReturnsElementsInAscendingOrder() {
        MinHeap<Integer> heap = new MinHeap<>(Comparator.naturalOrder());
        List.of(5, 3, 8, 1, 9, 2).forEach(heap::insert);

        List<Integer> drained = drain(heap);

        assertThat(drained).containsExactly(1, 2, 3, 5, 8, 9);
    }

    @Test
    void reversedComparatorYieldsHighestValueFirst() {
        // The technique DecisionListener relies on: a reversed comparator makes "min" mean
        // "highest priority", so extractMin surfaces the highest-risk transaction first.
        MinHeap<Integer> heap = new MinHeap<>(Comparator.<Integer>naturalOrder().reversed());
        List.of(5, 3, 8, 1, 9, 2).forEach(heap::insert);

        List<Integer> drained = drain(heap);

        assertThat(drained).containsExactly(9, 8, 5, 3, 2, 1);
    }

    @Test
    void singleElementRoundTrips() {
        MinHeap<Integer> heap = new MinHeap<>(Comparator.naturalOrder());
        heap.insert(42);

        assertThat(heap.size()).isEqualTo(1);
        assertThat(heap.peek()).isEqualTo(42);
        assertThat(heap.extractMin()).isEqualTo(42);
        assertThat(heap.isEmpty()).isTrue();
    }

    @Test
    void duplicatePrioritiesAreAllReturned() {
        MinHeap<Integer> heap = new MinHeap<>(Comparator.naturalOrder());
        List.of(4, 4, 4, 1, 1).forEach(heap::insert);

        assertThat(drain(heap)).containsExactly(1, 1, 4, 4, 4);
    }

    @Test
    void sizeAndIsEmptyTrackHeapContents() {
        MinHeap<Integer> heap = new MinHeap<>(Comparator.naturalOrder());
        assertThat(heap.isEmpty()).isTrue();
        assertThat(heap.size()).isZero();

        heap.insert(1);
        heap.insert(2);
        assertThat(heap.isEmpty()).isFalse();
        assertThat(heap.size()).isEqualTo(2);

        heap.extractMin();
        assertThat(heap.size()).isEqualTo(1);
    }

    @Test
    void extractMinOnEmptyHeapThrows() {
        MinHeap<Integer> heap = new MinHeap<>(Comparator.naturalOrder());

        assertThatThrownBy(heap::extractMin).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void peekOnEmptyHeapThrows() {
        MinHeap<Integer> heap = new MinHeap<>(Comparator.naturalOrder());

        assertThatThrownBy(heap::peek).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void peekDoesNotRemoveTheElement() {
        MinHeap<Integer> heap = new MinHeap<>(Comparator.naturalOrder());
        heap.insert(7);
        heap.insert(3);

        assertThat(heap.peek()).isEqualTo(3);
        assertThat(heap.size()).isEqualTo(2);
    }

    private static <E> List<E> drain(MinHeap<E> heap) {
        List<E> result = new ArrayList<>();
        while (!heap.isEmpty()) {
            result.add(heap.extractMin());
        }
        return result;
    }
}
