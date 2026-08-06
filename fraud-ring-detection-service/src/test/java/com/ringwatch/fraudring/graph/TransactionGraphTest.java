package com.ringwatch.fraudring.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransactionGraphTest {

    @Test
    void simpleChainWithNoLoopBackIsNotACycle() {
        TransactionGraph graph = new TransactionGraph();

        assertThat(graph.recordTransferAndDetectCycle("A", "B")).isEmpty();
        assertThat(graph.recordTransferAndDetectCycle("B", "C")).isEmpty();
    }

    @Test
    void threeNodeCycleIsDetectedWhenTheClosingEdgeIsAdded() {
        TransactionGraph graph = new TransactionGraph();
        graph.recordTransferAndDetectCycle("A", "B");
        graph.recordTransferAndDetectCycle("B", "C");

        Optional<List<String>> cycle = graph.recordTransferAndDetectCycle("C", "A");

        assertThat(cycle).isPresent();
        assertThat(cycle.get()).containsExactly("C", "A", "B", "C");
    }

    @Test
    void fourNodeCycleThroughMultipleIntermediariesIsDetected() {
        TransactionGraph graph = new TransactionGraph();
        graph.recordTransferAndDetectCycle("A", "B");
        graph.recordTransferAndDetectCycle("B", "C");
        graph.recordTransferAndDetectCycle("C", "D");

        Optional<List<String>> cycle = graph.recordTransferAndDetectCycle("D", "A");

        assertThat(cycle).isPresent();
        assertThat(cycle.get().getFirst()).isEqualTo(cycle.get().getLast());
        assertThat(cycle.get()).containsExactly("D", "A", "B", "C", "D");
    }

    @Test
    void selfTransferIsNotTreatedAsACycle() {
        TransactionGraph graph = new TransactionGraph();

        Optional<List<String>> cycle = graph.recordTransferAndDetectCycle("A", "A");

        assertThat(cycle).isEmpty();
    }

    @Test
    void branchingPathsThatDoNotLoopBackAreNotACycle() {
        TransactionGraph graph = new TransactionGraph();
        graph.recordTransferAndDetectCycle("A", "B");
        graph.recordTransferAndDetectCycle("A", "C");
        graph.recordTransferAndDetectCycle("B", "D");

        Optional<List<String>> cycle = graph.recordTransferAndDetectCycle("C", "D");

        // D is reachable from both branches, but there's no path back to C, so no cycle closes.
        assertThat(cycle).isEmpty();
    }

    @Test
    void repeatedIdenticalClosingEdgeIsDetectedEachTime() {
        // Documented, accepted behavior: re-sending the exact same cycle-closing transfer
        // re-reports the cycle rather than being deduplicated here - deduplication of repeated
        // identical rings is left to a future notification-layer polish pass, not this graph.
        TransactionGraph graph = new TransactionGraph();
        graph.recordTransferAndDetectCycle("A", "B");
        graph.recordTransferAndDetectCycle("B", "C");
        graph.recordTransferAndDetectCycle("C", "A");

        Optional<List<String>> secondDetection = graph.recordTransferAndDetectCycle("C", "A");

        assertThat(secondDetection).isPresent();
    }
}
