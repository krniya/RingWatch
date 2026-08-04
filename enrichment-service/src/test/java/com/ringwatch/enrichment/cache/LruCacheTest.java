package com.ringwatch.enrichment.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LruCacheTest {

    @Test
    void constructorRejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new LruCache<String, String>(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getOnMissingKeyReturnsEmpty() {
        LruCache<String, String> cache = new LruCache<>(2);
        assertThat(cache.get("missing")).isEmpty();
    }

    @Test
    void putAndGetRoundTrip() {
        LruCache<String, String> cache = new LruCache<>(2);
        cache.put("a", "1");
        assertThat(cache.get("a")).contains("1");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void exceedingCapacityEvictsLeastRecentlyUsed() {
        LruCache<String, String> cache = new LruCache<>(2);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.get("b")).contains("2");
        assertThat(cache.get("c")).contains("3");
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void getRefreshesRecencyProtectingFromEviction() {
        LruCache<String, String> cache = new LruCache<>(2);
        cache.put("a", "1");
        cache.put("b", "2");

        cache.get("a");
        cache.put("c", "3");

        assertThat(cache.get("a")).contains("1");
        assertThat(cache.get("b")).isEmpty();
        assertThat(cache.get("c")).contains("3");
    }

    @Test
    void puttingExistingKeyUpdatesValueAndRefreshesRecency() {
        LruCache<String, String> cache = new LruCache<>(2);
        cache.put("a", "1");
        cache.put("b", "2");

        cache.put("a", "updated");
        cache.put("c", "3");

        assertThat(cache.get("a")).contains("updated");
        assertThat(cache.get("b")).isEmpty();
        assertThat(cache.get("c")).contains("3");
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void tracksDifferentKeysIndependentlyUpToCapacity() {
        LruCache<String, Integer> cache = new LruCache<>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        assertThat(cache.get("a")).contains(1);
        assertThat(cache.get("b")).contains(2);
        assertThat(cache.get("c")).contains(3);
        assertThat(cache.size()).isEqualTo(3);
    }

    @Test
    void capacityOneEvictsThePreviousEntryOnEveryNewKey() {
        LruCache<String, String> cache = new LruCache<>(1);
        cache.put("a", "1");
        cache.put("b", "2");

        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.get("b")).contains("2");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void capacityOneKeepsUpdatingTheSameKeyWithoutEviction() {
        LruCache<String, String> cache = new LruCache<>(1);
        cache.put("a", "1");
        cache.put("a", "2");

        assertThat(cache.get("a")).contains("2");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void computeAndPutAppliesDefaultWhenKeyIsAbsentAndStoresRemappedResult() {
        LruCache<String, Integer> cache = new LruCache<>(2);

        Integer previous = cache.computeAndPut("a", 0, v -> v + 1);

        assertThat(previous).isZero();
        assertThat(cache.get("a")).contains(1);
    }

    @Test
    void computeAndPutReturnsPriorValueAndAccumulatesOnRepeatedCalls() {
        LruCache<String, Integer> cache = new LruCache<>(2);
        cache.computeAndPut("a", 0, v -> v + 1);

        Integer previous = cache.computeAndPut("a", 0, v -> v + 1);

        assertThat(previous).isEqualTo(1);
        assertThat(cache.get("a")).contains(2);
    }

    @Test
    void computeAndPutIsAtomicUnderConcurrentIncrementsOnTheSameKey() throws InterruptedException {
        LruCache<String, Integer> cache = new LruCache<>(4);
        int threads = 8;
        int incrementsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        cache.computeAndPut("counter", 0, v -> v + 1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(cache.get("counter")).contains(threads * incrementsPerThread);
    }
}
