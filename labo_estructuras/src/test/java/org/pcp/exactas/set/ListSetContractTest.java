package org.pcp.exactas.set;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ListSetContractTest {
    static Stream<Supplier<ConcurrentIntSet>> implementations() {
        return Stream.of(SequentialListSet::new, CoarseListSet::new, FineListSet::new,
                OptimisticListSet::new, LazyListSet::new, LockFreeListSet::new);
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void basicContract(Supplier<ConcurrentIntSet> factory) {
        ConcurrentIntSet set = factory.get();
        ImplementationAssumptions.assumeImplemented(set);
        String implementation = name(set);
        assertFalse(set.contains(7), implementation + ": empty set must not contain 7");
        assertTrue(set.add(7), implementation + ": first add(7) must succeed");
        assertFalse(set.add(7), implementation + ": duplicate add(7) must return false");
        assertTrue(set.contains(7), implementation + ": set must contain 7 after successful insertion");
        assertTrue(set.remove(7), implementation + ": remove(7) must succeed for a present key");
        assertFalse(set.remove(7), implementation + ": second remove(7) must return false");
        assertFalse(set.contains(7), implementation + ": set must not contain 7 after removal");
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void insertionOrdersAndPositions(Supplier<ConcurrentIntSet> factory) {
        ConcurrentIntSet set = factory.get();
        ImplementationAssumptions.assumeImplemented(set);
        String implementation = name(set);
        for (int i = 1; i <= 100; i++) assertTrue(set.add(i), implementation + ": ascending add(" + i + ") must succeed");
        for (int i = 200; i > 100; i--) assertTrue(set.add(i), implementation + ": descending add(" + i + ") must succeed");
        List<Integer> randomOrder = new ArrayList<>();
        for (int i = 0; i < 100; i++) randomOrder.add(1_000 + i);
        Collections.shuffle(randomOrder, new Random(8172));
        for (int value : randomOrder) assertTrue(set.add(value), implementation + ": shuffled unique add(" + value + ") must succeed");
        for (int i = 1; i <= 200; i++) assertTrue(set.contains(i), implementation + ": must contain inserted key " + i);
        assertTrue(set.remove(1), implementation + ": removal near the head must succeed");
        assertTrue(set.remove(100), implementation + ": removal in the middle must succeed");
        assertTrue(set.remove(200), implementation + ": removal near the tail must succeed");
        assertFalse(set.contains(1), implementation + ": removed head-side key 1 must be absent");
        assertFalse(set.contains(100), implementation + ": removed middle key 100 must be absent");
        assertFalse(set.contains(200), implementation + ": removed tail-side key 200 must be absent");
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void repeatedAddRemove(Supplier<ConcurrentIntSet> factory) {
        ConcurrentIntSet set = factory.get();
        ImplementationAssumptions.assumeImplemented(set);
        String implementation = name(set);
        for (int i = 0; i < 1_000; i++) {
            assertTrue(set.add(42), implementation + ": iteration " + i + " first add(42) must succeed");
            assertFalse(set.add(42), implementation + ": iteration " + i + " duplicate add(42) must fail");
            assertTrue(set.remove(42), implementation + ": iteration " + i + " first remove(42) must succeed");
            assertFalse(set.remove(42), implementation + ": iteration " + i + " duplicate remove(42) must fail");
        }
    }

    private static String name(ConcurrentIntSet set) { return set.getClass().getSimpleName(); }
}
