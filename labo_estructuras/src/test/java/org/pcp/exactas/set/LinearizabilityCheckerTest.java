package org.pcp.exactas.set;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LinearizabilityCheckerTest {
    @Test
    void recognizesKnownHistories() {
        LinearizabilityChecker checker = new LinearizabilityChecker();
        List<LinearizabilityChecker.Operation> linearizable = List.of(
                new LinearizabilityChecker.Operation(0, 1, 2, LinearizabilityChecker.Type.ADD, 1, true),
                new LinearizabilityChecker.Operation(1, 3, 4, LinearizabilityChecker.Type.CONTAINS, 1, true));
        assertTrue(checker.isLinearizable(linearizable),
                "The checker must accept this known linearizable history: " + linearizable);
        List<LinearizabilityChecker.Operation> nonLinearizable = List.of(
                new LinearizabilityChecker.Operation(0, 1, 2, LinearizabilityChecker.Type.ADD, 1, false));
        assertFalse(checker.isLinearizable(nonLinearizable),
                "The checker must reject an add(1) that falsely returned false on an empty set: " + nonLinearizable);
    }

    static Stream<Supplier<ConcurrentIntSet>> implementations() {
        return Stream.of(CoarseListSet::new, FineListSet::new, OptimisticListSet::new,
                LazyListSet::new, LockFreeListSet::new);
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void tinyConcurrentHistoriesAreLinearizable(Supplier<ConcurrentIntSet> factory) {
        ConcurrentIntSet probe = factory.get();
        String implementation = probe.getClass().getSimpleName();
        ImplementationAssumptions.assumeImplemented(probe);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (int round = 0; round < 10; round++) {
                List<LinearizabilityChecker.Operation> history = runHistory(factory.get(), round);
                int historyRound = round;
                assertTrue(new LinearizabilityChecker().isLinearizable(history),
                        () -> implementation + ": generated history in round " + historyRound + " is not linearizable: " + history);
            }
        }, implementation + ": bounded linearizability histories must finish; timeout may indicate deadlock");
    }

    private static List<LinearizabilityChecker.Operation> runHistory(ConcurrentIntSet set, int round) throws Exception {
        LinearizabilityChecker.Type[][] scripts = {
                {LinearizabilityChecker.Type.ADD, LinearizabilityChecker.Type.REMOVE},
                {LinearizabilityChecker.Type.CONTAINS, LinearizabilityChecker.Type.ADD},
                {LinearizabilityChecker.Type.ADD, LinearizabilityChecker.Type.CONTAINS}
        };
        AtomicLong sequence = new AtomicLong();
        List<LinearizabilityChecker.Operation> history = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            for (int thread = 0; thread < 3; thread++) {
                final int id = thread;
                executor.submit(() -> {
                    ready.countDown(); start.await();
                    for (int i = 0; i < 2; i++) {
                        LinearizabilityChecker.Type type = scripts[id][i];
                        int key = (id + i + round) % 3;
                        long invocation = sequence.incrementAndGet();
                        boolean result = switch (type) {
                            case ADD -> set.add(key);
                            case REMOVE -> set.remove(key);
                            case CONTAINS -> set.contains(key);
                        };
                        long response = sequence.incrementAndGet();
                        history.add(new LinearizabilityChecker.Operation(id, invocation, response, type, key, result));
                    }
                    return null;
                });
            }
            assertTrue(ready.await(3, TimeUnit.SECONDS),
                    "Not all three history workers reached the simultaneous-start barrier in round " + round);
            start.countDown();
        } finally { executor.shutdown(); }
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
                "History workers did not terminate in round " + round + "; partial history=" + history);
        return history;
    }
}
