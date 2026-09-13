package org.pcp.exactas.set;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/** JMH workloads for comparing costs and guarantees, not proving progress properties. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class SetBenchmarks {
    @Benchmark
    @Threads(1)
    public boolean singleThreadBaseline(SingleThreadState state) {
        return state.perform();
    }

    @Benchmark
    public boolean concurrentWorkload(ConcurrentState state) {
        return state.perform();
    }

    @State(Scope.Benchmark)
    public static class SingleThreadState extends BaseState {
        @Param({"SEQUENTIAL", "COARSE", "FINE", "OPTIMISTIC", "LAZY", "LOCK_FREE"})
        public Implementation implementation;

        @Setup(Level.Iteration) public void setup() { initialize(implementation, Workload.READ_HEAVY); }
    }

    @State(Scope.Benchmark)
    public static class ConcurrentState extends BaseState {
        @Param({"COARSE", "FINE", "OPTIMISTIC", "LAZY", "LOCK_FREE"})
        public Implementation implementation;
        @Param({"READ_ONLY", "READ_HEAVY", "MIXED_LOW_CONFLICT", "HOTSPOT", "UPDATE_HEAVY"})
        public Workload workload;

        @Setup(Level.Iteration) public void setup() { initialize(implementation, workload); }
    }

    @State(Scope.Benchmark)
    public abstract static class BaseState {
        @Param({"256", "1024"}) public int listLength;
        private ConcurrentIntSet set;
        private Workload workload;
        private int domain;

        final void initialize(Implementation implementation, Workload workload) {
            this.set = implementation.create();
            this.workload = workload;
            this.domain = listLength * 2;
            for (int key = 0; key < domain; key += 2) set.add(key);
        }

        final boolean perform() {
            int ticket = ThreadLocalRandom.current().nextInt(100);
            int key = chooseKey();
            if (ticket < workload.containsPercent) return set.contains(key);
            if (ticket < workload.containsPercent + workload.addPercent) return set.add(key);
            return set.remove(key);
        }

        private int chooseKey() {
            if (workload == Workload.HOTSPOT && ThreadLocalRandom.current().nextInt(100) < 80) {
                int middle = domain / 2;
                return middle - 16 + ThreadLocalRandom.current().nextInt(32);
            }
            return ThreadLocalRandom.current().nextInt(domain);
        }
    }

    public enum Implementation {
        SEQUENTIAL { ConcurrentIntSet create() { return new SequentialListSet(); } },
        COARSE { ConcurrentIntSet create() { return new CoarseListSet(); } },
        FINE { ConcurrentIntSet create() { return new FineListSet(); } },
        OPTIMISTIC { ConcurrentIntSet create() { return new OptimisticListSet(); } },
        LAZY { ConcurrentIntSet create() { return new LazyListSet(); } },
        LOCK_FREE { ConcurrentIntSet create() { return new LockFreeListSet(); } };
        abstract ConcurrentIntSet create();
    }

    public enum Workload {
        READ_ONLY(100, 0, 0), READ_HEAVY(90, 5, 5), MIXED_LOW_CONFLICT(50, 25, 25),
        HOTSPOT(50, 25, 25), UPDATE_HEAVY(10, 45, 45);
        final int containsPercent, addPercent, removePercent;
        Workload(int containsPercent, int addPercent, int removePercent) {
            this.containsPercent = containsPercent; this.addPercent = addPercent; this.removePercent = removePercent;
        }
    }
}
