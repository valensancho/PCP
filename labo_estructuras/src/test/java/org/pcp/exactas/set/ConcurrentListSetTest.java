package org.pcp.exactas.set;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ConcurrentListSetTest {
    static Stream<Supplier<ConcurrentIntSet>> concurrentImplementations() {
        return Stream.of(CoarseListSet::new, FineListSet::new, OptimisticListSet::new,
                LazyListSet::new, LockFreeListSet::new);
    }

    @ParameterizedTest
    @MethodSource("concurrentImplementations")
    void duplicateInsertionAndRemoval(Supplier<ConcurrentIntSet> factory) {
        ConcurrentIntSet probe = factory.get();
        String implementation = probe.getClass().getSimpleName();
        ImplementationAssumptions.assumeImplemented(probe);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ConcurrentIntSet set = factory.get();
            List<Boolean> adds = simultaneous(8, () -> set.add(42));
            assertEquals(1, adds.stream().filter(Boolean::booleanValue).count(),
                    implementation + ": exactly one concurrent add(42) must succeed; results=" + adds);
            assertTrue(set.contains(42), implementation + ": 42 must be present after the winning add");
            List<Boolean> removes = simultaneous(8, () -> set.remove(42));
            assertEquals(1, removes.stream().filter(Boolean::booleanValue).count(),
                    implementation + ": exactly one concurrent remove(42) must succeed; results=" + removes);
            assertFalse(set.contains(42), implementation + ": 42 must be absent after the winning removal");
        }, implementation + ": duplicate-key races must finish; timeout may indicate deadlock");
    }

    @ParameterizedTest
    @MethodSource("concurrentImplementations")
    void sameWindowAndAdjacentRemoval(Supplier<ConcurrentIntSet> factory) {
        ConcurrentIntSet probe = factory.get();
        String implementation = probe.getClass().getSimpleName();
        ImplementationAssumptions.assumeImplemented(probe);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ConcurrentIntSet set = factory.get();
            set.add(10); set.add(40);
            List<Boolean> inserted = simultaneous(List.of(() -> set.add(20), () -> set.add(30)));
            assertEquals(List.of(true, true), inserted,
                    implementation + ": both same-window insertions must succeed; results=" + inserted);
            assertTrue(set.contains(20), implementation + ": 20 must survive concurrent same-window insertion");
            assertTrue(set.contains(30), implementation + ": 30 must survive concurrent same-window insertion");
            set.add(10); set.add(20); set.add(30);
            List<Boolean> removed = simultaneous(List.of(() -> set.remove(10), () -> set.remove(20)));
            assertEquals(List.of(true, true), removed,
                    implementation + ": both adjacent removals must succeed; results=" + removed);
            assertFalse(set.contains(10), implementation + ": 10 must be absent after adjacent removal");
            assertFalse(set.contains(20), implementation + ": 20 must be absent after adjacent removal");
            assertTrue(set.contains(30), implementation + ": unaffected key 30 must remain present");
        }, implementation + ": same-window insertion and adjacent removal must finish; timeout may indicate deadlock");
    }

    @ParameterizedTest
    @MethodSource("concurrentImplementations")
    void disjointRanges(Supplier<ConcurrentIntSet> factory) {
        ConcurrentIntSet probe = factory.get();
        String implementation = probe.getClass().getSimpleName();
        ImplementationAssumptions.assumeImplemented(probe);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ConcurrentIntSet set = factory.get();
            List<Callable<Boolean>> adds = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                int start = thread * 100;
                adds.add(() -> { for (int i = start; i < start + 100; i++) if (!set.add(i)) return false; return true; });
            }
            List<Boolean> addResults = simultaneous(adds);
            assertTrue(addResults.stream().allMatch(Boolean::booleanValue),
                    implementation + ": every disjoint-range insertion must succeed; results=" + addResults);
            for (int i = 0; i < 400; i++) assertTrue(set.contains(i), implementation + ": inserted disjoint-range key must be present: " + i);
            List<Callable<Boolean>> removes = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                int start = thread * 100;
                removes.add(() -> { for (int i = start; i < start + 100; i++) if (!set.remove(i)) return false; return true; });
            }
            List<Boolean> removeResults = simultaneous(removes);
            assertTrue(removeResults.stream().allMatch(Boolean::booleanValue),
                    implementation + ": every disjoint-range removal must succeed; results=" + removeResults);
            for (int i = 0; i < 400; i++) assertFalse(set.contains(i), implementation + ": removed disjoint-range key must be absent: " + i);
        }, implementation + ": disjoint-range operations must finish; timeout may indicate deadlock");
    }

    private static List<Boolean> simultaneous(int threads, Callable<Boolean> work) throws Exception {
        List<Callable<Boolean>> workItems = new ArrayList<>();
        for (int i = 0; i < threads; i++) workItems.add(work);
        return simultaneous(workItems);
    }

    private static List<Boolean> simultaneous(List<Callable<Boolean>> work) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(work.size());
        CountDownLatch ready = new CountDownLatch(work.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> item : work) futures.add(executor.submit(() -> { ready.countDown(); start.await(); return item.call(); }));
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "Not all " + work.size() + " workers reached the simultaneous-start barrier");
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) results.add(future.get());
            return results;
        } finally { executor.shutdownNow(); }
    }
}
