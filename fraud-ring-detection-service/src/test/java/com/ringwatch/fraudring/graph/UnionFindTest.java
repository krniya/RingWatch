package com.ringwatch.fraudring.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnionFindTest {

    @Test
    void unseenKeysAreTheirOwnDisjointSet() {
        UnionFind uf = new UnionFind();

        assertThat(uf.connected("a", "b")).isFalse();
        assertThat(uf.find("a")).isEqualTo("a");
    }

    @Test
    void unionMakesTwoKeysConnected() {
        UnionFind uf = new UnionFind();
        uf.union("a", "b");

        assertThat(uf.connected("a", "b")).isTrue();
        assertThat(uf.find("a")).isEqualTo(uf.find("b"));
    }

    @Test
    void unionIsTransitiveAcrossAChain() {
        UnionFind uf = new UnionFind();
        uf.union("a", "b");
        uf.union("b", "c");
        uf.union("c", "d");

        assertThat(uf.connected("a", "d")).isTrue();
        assertThat(uf.find("a")).isEqualTo(uf.find("d"));
    }

    @Test
    void separateChainsStayDisjointUntilLinked() {
        UnionFind uf = new UnionFind();
        uf.union("a", "b");
        uf.union("x", "y");

        assertThat(uf.connected("a", "x")).isFalse();

        uf.union("b", "x");

        assertThat(uf.connected("a", "y")).isTrue();
    }

    @Test
    void redundantUnionOfAlreadyConnectedKeysIsANoOp() {
        UnionFind uf = new UnionFind();
        uf.union("a", "b");
        String rootBefore = uf.find("a");

        uf.union("a", "b");
        uf.union("b", "a");

        assertThat(uf.find("a")).isEqualTo(rootBefore);
        assertThat(uf.connected("a", "b")).isTrue();
    }

    @Test
    void findCompressesPathsSoRepeatedLookupsShareTheSameRoot() {
        UnionFind uf = new UnionFind();
        uf.union("a", "b");
        uf.union("b", "c");
        uf.union("c", "d");
        uf.union("d", "e");

        String root = uf.find("e");
        // After compression every key in the chain should resolve to the same root in one hop.
        assertThat(uf.find("a")).isEqualTo(root);
        assertThat(uf.find("b")).isEqualTo(root);
        assertThat(uf.find("c")).isEqualTo(root);
        assertThat(uf.find("d")).isEqualTo(root);
    }

    @Test
    void unionByRankKeepsBothOriginalRootsAsValidMembersOfTheMergedSet() {
        UnionFind uf = new UnionFind();
        uf.union("a", "b");
        uf.union("c", "d");
        // Two rank-1 trees merging: whichever root wins, both a/b and c/d must end up connected.
        uf.union("a", "c");

        assertThat(uf.connected("a", "b")).isTrue();
        assertThat(uf.connected("c", "d")).isTrue();
        assertThat(uf.connected("a", "d")).isTrue();
    }
}
