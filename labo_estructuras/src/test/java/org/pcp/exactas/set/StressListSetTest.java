package org.pcp.exactas.set;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StressListSetTest {
    static java.util.stream.Stream<Supplier<ConcurrentIntSet>> implementations() {
        return java.util.stream.Stream.of(CoarseListSet::new, FineListSet::new, OptimisticListSet::new,
                LazyListSet::new, LockFreeListSet::new);
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void seededMixedWorkloadCompletes(Supplier<ConcurrentIntSet> factory) {
        ConcurrentIntSet probe = factory.get();
        String implementation = probe.getClass().getSimpleName();
        ImplementationAssumptions.assumeImplemented(probe);
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            long seed = Long.getLong("seed", 19_847L);
            ConcurrentIntSet set = factory.get();
            int threads = 24, perThread = 6_000;
            CountDownLatch ready = new CountDownLatch(threads), start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                for (int t = 0; t < threads; t++) {
                    int id = t;
                    executor.submit(() -> {
                        Random random = new Random(seed + id);
                        ready.countDown();
                        start.await();
                        int base = id * 10_000;
                        for (int i = 0; i < perThread; i++) {
                            int key = base + random.nextInt(1_000);
                            switch (random.nextInt(3)) { case 0 -> set.add(key); case 1 -> set.remove(key); default -> set.contains(key); }
                        }
                        return null;
                    });
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS),
                        implementation + ": not all " + threads + " stress workers reached the start barrier; seed=" + seed);
                start.countDown();
            } finally { executor.shutdown(); }
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS),
                    implementation + ": stress workers did not terminate; seed=" + seed
                            + ", threads=" + threads + ", operationsPerThread=" + perThread);
        }, implementation + ": seeded mixed workload timed out; seed=" + Long.getLong("seed", 19_847L));
    }
}
