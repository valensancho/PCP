package org.pcp.exactas.set;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class SetPerformanceBenchmark {

    // CONFIGURACIÓN DEL EXPERIMENTO

    private static final int[] THREAD_COUNTS = {16};
    private static final int KEY_SPACE = 1_024;
    private static final KeyDistribution KEY_DISTRIBUTION = KeyDistribution.CLUSTERED;
    private static final double CLUSTERED_ACCESS_PROBABILITY = 0.95;
    private static final int CLUSTERED_REGION_SIZE = 8;
    private static final int WARMUP_REPETITIONS = 1; // Ayuda a elimiar ruido del JIT y otras caches internas de la JVM
    private static final int MEASURED_REPETITIONS = 6;
    private static final int OPERATIONS_PER_THREAD = 5_000;
    private static final long BASE_SEED = 0x5eed;
    private static final String CSV_FILE = "set-performance-results.csv";

    private SetPerformanceBenchmark() {
    }

    enum KeyDistribution {
        UNIFORM,
        CLUSTERED
    }

    enum Workload {
        READ_ONLY(1.00, 0.00, 0.00),
        READ_HEAVY(0.90, 0.05, 0.05),
        BALANCED(0.50, 0.25, 0.25),
        WRITE_HEAVY(0.10, 0.45, 0.45),
        ALMOST_ALL_WRITES(0.02, 0.49, 0.49);

        final double containsProbability;
        final double addProbability;
        final double removeProbability;

        Workload(double containsProbability, double addProbability, double removeProbability) {
            this.containsProbability = containsProbability;
            this.addProbability = addProbability;
            this.removeProbability = removeProbability;
        }
    }

    enum Operation {
        CONTAINS("contains"), ADD("add"), REMOVE("remove");

        final String csvName;

        Operation(String csvName) {
            this.csvName = csvName;
        }
    }

    enum Implementation {
        SEQUENTIAL("Sequential", "SequentialListSet", SequentialListSet::new, false),
        COARSE("Coarse", "CoarseListSet", CoarseListSet::new, true),
        FINE("Fine", "FineListSet", FineListSet::new, true),
        OPTIMISTIC("Optimistic", "OptimisticListSet", OptimisticListSet::new, true),
        LAZY("Lazy", "LazyListSet", LazyListSet::new, true),
        LOCK_FREE("LockFree", "LockFreeListSet", LockFreeListSet::new, true);

        final String displayName;
        final String csvName;
        final Supplier<ConcurrentIntSet> factory;
        final boolean concurrent;

        Implementation(String displayName, String csvName, Supplier<ConcurrentIntSet> factory,
                       boolean concurrent) {
            this.displayName = displayName;
            this.csvName = csvName;
            this.factory = factory;
            this.concurrent = concurrent;
        }
    }

    record OperationStats(Operation operation, int samples, double successRate, double meanMicros,
                          double stddevMicros, double p90Micros, double p95Micros,
                          double p99Micros) {
    }

    record BenchmarkResult(Implementation implementation, Workload workload, int threadCount,
                           OperationStats contains, OperationStats add, OperationStats remove,
                           double meanThroughput) {
    }

    record RepetitionResult(long wallElapsedNanos, WorkerSamples[] samples) {
    }

    /** Each worker writes only to its own arrays; no measurement bookkeeping is shared. */
    static final class WorkerSamples {
        final long[] containsLatencies = new long[OPERATIONS_PER_THREAD];
        final long[] addLatencies = new long[OPERATIONS_PER_THREAD];
        final long[] removeLatencies = new long[OPERATIONS_PER_THREAD];
        int containsCount;
        int addCount;
        int removeCount;
        long containsTrue;
        long addTrue;
        long removeTrue;

        void record(Operation operation, long elapsedNanos, boolean result) {
            switch (operation) {
                case CONTAINS -> {
                    containsLatencies[containsCount++] = elapsedNanos;
                    if (result) containsTrue++;
                }
                case ADD -> {
                    addLatencies[addCount++] = elapsedNanos;
                    if (result) addTrue++;
                }
                case REMOVE -> {
                    removeLatencies[removeCount++] = elapsedNanos;
                    if (result) removeTrue++;
                }
            }
        }
    }

    /** A tiny primitive-only buffer used only while one configuration is aggregated. */
    static final class LongSamples {
        private long[] values = new long[16];
        private int size;

        void addAll(long[] source, int count) {
            ensureCapacity(size + count);
            System.arraycopy(source, 0, values, size, count);
            size += count;
        }

        int size() {
            return size;
        }

        long[] sortedCopy() {
            long[] copy = Arrays.copyOf(values, size);
            Arrays.sort(copy);
            return copy;
        }

        private void ensureCapacity(int required) {
            if (required <= values.length) return;
            int newCapacity = Math.max(required, values.length * 2);
            values = Arrays.copyOf(values, newCapacity);
        }
    }

    public static void main(String[] args) throws Exception {
        validateConfiguration();
        printStartup();
        Path csvPath = Path.of(CSV_FILE);

        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8);
             PrintWriter csv = new PrintWriter(bufferedWriter)) {
            csv.println("implementation,workload,key_distribution,threads,operation,samples,success_rate,mean_us,"
                    + "stddev_us,p90_us,p95_us,p99_us,throughput_ops_s");

            for (Workload workload : Workload.values()) {
                for (int threadCount : THREAD_COUNTS) {
                    BenchmarkResult[] results = new BenchmarkResult[Implementation.values().length];
                    for (Implementation implementation : Implementation.values()) {
                        if (!implementation.concurrent && threadCount > 1) continue;
                        BenchmarkResult result = runConfiguration(implementation, workload, threadCount);
                        results[implementation.ordinal()] = result;
                        writeCsvRows(csv, result);
                        csv.flush();
                    }
                    printSummary(workload, threadCount, results);
                }
            }
        }

        System.out.println("\nCSV results written to " + csvPath.toAbsolutePath());
    }

    private static void printStartup() {
        System.out.println("Concurrent Set Benchmark");
        System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name"));
        System.out.println("Warmup repetitions: " + WARMUP_REPETITIONS);
        System.out.println("Measured repetitions: " + MEASURED_REPETITIONS);
        System.out.println("Operations/thread: " + OPERATIONS_PER_THREAD);
        System.out.println("Key space: " + KEY_SPACE);
        System.out.println("Key distribution: " + KEY_DISTRIBUTION);
        if (KEY_DISTRIBUTION == KeyDistribution.CLUSTERED) {
            int firstClusteredKey = clusteredRegionStart();
            int lastClusteredKey = firstClusteredKey + CLUSTERED_REGION_SIZE - 1;
            System.out.printf(Locale.ROOT, "Clustered keys: %.0f%% in %d..%d%n",
                    CLUSTERED_ACCESS_PROBABILITY * 100, firstClusteredKey, lastClusteredKey);
        }
    }

    private static BenchmarkResult runConfiguration(Implementation implementation, Workload workload,
                                                    int threadCount) throws InterruptedException {
        System.out.printf("%nRunning %s / %d threads / %s ...%n",
                workload, threadCount, implementation.csvName);

        for (int repetition = 0; repetition < WARMUP_REPETITIONS; repetition++) {
            System.out.printf("  warmup %d/%d%n", repetition + 1, WARMUP_REPETITIONS);
            runRepetition(implementation, workload, threadCount, repetition, true, false);
        }

        LongSamples contains = new LongSamples();
        LongSamples add = new LongSamples();
        LongSamples remove = new LongSamples();
        long containsTrue = 0;
        long addTrue = 0;
        long removeTrue = 0;
        double[] throughputs = new double[MEASURED_REPETITIONS];

        for (int repetition = 0; repetition < MEASURED_REPETITIONS; repetition++) {
            System.out.printf("  repetition %d/%d%n", repetition + 1, MEASURED_REPETITIONS);
            RepetitionResult repetitionResult = runRepetition(
                    implementation, workload, threadCount, repetition, false, true);
            throughputs[repetition] = operationsPerRepetition(threadCount)
                    / (repetitionResult.wallElapsedNanos / 1_000_000_000.0);

            for (WorkerSamples samples : repetitionResult.samples) {
                contains.addAll(samples.containsLatencies, samples.containsCount);
                add.addAll(samples.addLatencies, samples.addCount);
                remove.addAll(samples.removeLatencies, samples.removeCount);
                containsTrue += samples.containsTrue;
                addTrue += samples.addTrue;
                removeTrue += samples.removeTrue;
            }
        }

        return new BenchmarkResult(implementation, workload, threadCount,
                computeStats(Operation.CONTAINS, contains, containsTrue),
                computeStats(Operation.ADD, add, addTrue),
                computeStats(Operation.REMOVE, remove, removeTrue), mean(throughputs));
    }

    private static RepetitionResult runRepetition(Implementation implementation, Workload workload,
                                                  int threadCount, int repetition, boolean warmup,
                                                  boolean collectSamples) throws InterruptedException {
        ConcurrentIntSet set = implementation.factory.get();
        prepopulate(set);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        WorkerSamples[] samples = collectSamples ? new WorkerSamples[threadCount] : null;

        for (int threadId = 0; threadId < threadCount; threadId++) {
            int workerId = threadId;
            WorkerSamples workerSamples = collectSamples ? new WorkerSamples() : null;
            if (collectSamples) samples[workerId] = workerSamples;
            Thread worker = Thread.ofPlatform().name("set-benchmark-" + workerId).unstarted(() -> {
                ready.countDown();
                try {
                    start.await();
                    workerLoop(set, workload, seedFor(workload, threadCount, warmup, repetition, workerId),
                            workerSamples);
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                } finally {
                    done.countDown();
                }
            });
            worker.start();
        }

        ready.await();
        long wallStart = System.nanoTime();
        start.countDown();
        done.await();
        long wallElapsedNanos = System.nanoTime() - wallStart;

        Throwable workerFailure = failure.get();
        if (workerFailure != null) {
            throw new IllegalStateException("Worker failed for " + implementation.csvName + " / "
                    + workload + " / " + threadCount + " threads", workerFailure);
        }
        return new RepetitionResult(wallElapsedNanos, samples);
    }

    private static void workerLoop(ConcurrentIntSet set, Workload workload, long seed,
                                   WorkerSamples samples) {
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
            // RNG cost is intentionally outside the timed set operation.
            Operation operation = chooseOperation(random, workload);
            int key = chooseKey(random);

            long start = System.nanoTime();
            boolean result = switch (operation) {
                case CONTAINS -> set.contains(key);
                case ADD -> set.add(key);
                case REMOVE -> set.remove(key);
            };
            long elapsedNanos = System.nanoTime() - start;

            if (samples != null) samples.record(operation, elapsedNanos, result);
        }
    }

    private static Operation chooseOperation(SplittableRandom random, Workload workload) {
        double probability = random.nextDouble();
        if (probability < workload.containsProbability) return Operation.CONTAINS;
        if (probability < workload.containsProbability + workload.addProbability) return Operation.ADD;
        return Operation.REMOVE;
    }

    private static int chooseKey(SplittableRandom random) {
        if (KEY_DISTRIBUTION == KeyDistribution.CLUSTERED
                && random.nextDouble() < CLUSTERED_ACCESS_PROBABILITY) {
            return clusteredRegionStart() + random.nextInt(CLUSTERED_REGION_SIZE);
        }
        return random.nextInt(KEY_SPACE);
    }

    private static int clusteredRegionStart() {
        return (KEY_SPACE - CLUSTERED_REGION_SIZE) / 2;
    }

    private static void validateConfiguration() {
        if (KEY_SPACE <= 0) {
            throw new IllegalStateException("KEY_SPACE must be positive");
        }
        if (CLUSTERED_REGION_SIZE <= 0 || CLUSTERED_REGION_SIZE > KEY_SPACE) {
            throw new IllegalStateException("CLUSTERED_REGION_SIZE must be between 1 and KEY_SPACE");
        }
        if (CLUSTERED_ACCESS_PROBABILITY < 0.0 || CLUSTERED_ACCESS_PROBABILITY > 1.0) {
            throw new IllegalStateException("CLUSTERED_ACCESS_PROBABILITY must be between 0.0 and 1.0");
        }
    }

    private static long seedFor(Workload workload, int threadCount, boolean warmup, int repetition,
                                int threadId) {
        // No implementation identifier appears here: each implementation sees equivalent streams.
        return BASE_SEED
                + workload.ordinal() * 1_000_000_000L
                + threadCount * 10_000_000L
                + (warmup ? 1_000_000L : 0)
                + repetition * 100_000L
                + threadId;
    }

    private static void prepopulate(ConcurrentIntSet set) {
        for (int key = 0; key < KEY_SPACE; key += 2) set.add(key);
        if (!set.contains(0) || set.contains(1)) {
            throw new IllegalStateException("Prepopulation did not create the expected even-key set");
        }
    }

    private static long operationsPerRepetition(int threadCount) {
        return (long) threadCount * OPERATIONS_PER_THREAD;
    }

    private static OperationStats computeStats(Operation operation, LongSamples samples, long trueCount) {
        if (samples.size() == 0) return null;
        double meanNanos = mean(samples.values, samples.size());
        double variance = 0.0;
        for (int i = 0; i < samples.size(); i++) {
            double difference = samples.values[i] - meanNanos;
            variance += difference * difference;
        }
        double stddevNanos = Math.sqrt(variance / samples.size());
        long[] sorted = samples.sortedCopy();
        return new OperationStats(operation, samples.size(), trueCount / (double) samples.size(),
                meanNanos / 1_000.0, stddevNanos / 1_000.0,
                percentile(sorted, 0.90) / 1_000.0,
                percentile(sorted, 0.95) / 1_000.0,
                percentile(sorted, 0.99) / 1_000.0);
    }

    private static double mean(long[] values, int count) {
        double sum = 0.0;
        for (int i = 0; i < count; i++) sum += values[i];
        return sum / count;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        index = Math.max(0, Math.min(index, sorted.length - 1));
        return sorted[index];
    }

    private static void printSummary(Workload workload, int threadCount, BenchmarkResult[] results) {
        System.out.printf("%n%s / %s - %d threads%n", KEY_DISTRIBUTION, workload, threadCount);
        System.out.printf(Locale.ROOT, "contains=%.0f%%, add=%.0f%%, remove=%.0f%%%n",
                workload.containsProbability * 100, workload.addProbability * 100,
                workload.removeProbability * 100);
        System.out.println("Implementation  Operation  Samples    Success%    Mean us    Std us    P90 us    P95 us    P99 us");
        System.out.println("----------------------------------------------------------------------------------------------------");
        for (BenchmarkResult result : results) {
            if (result == null) continue;
            printStatsRow(result.implementation.displayName, result.contains);
            printStatsRow(result.implementation.displayName, result.add);
            printStatsRow(result.implementation.displayName, result.remove);
        }

        System.out.println("\nImplementation  Throughput ops/s");
        System.out.println("---------------------------------");
        for (BenchmarkResult result : results) {
            if (result != null) {
                System.out.printf(Locale.ROOT, "%-15s %18.0f%n",
                        result.implementation.displayName, result.meanThroughput);
            }
        }
    }

    private static void printStatsRow(String implementation, OperationStats stats) {
        if (stats == null) return; // READ_ONLY deliberately has no add/remove rows.
        System.out.printf(Locale.ROOT,
                "%-15s %-10s %,9d %10.2f %10.3f %9.3f %9.3f %9.3f %9.3f%n",
                implementation, stats.operation.csvName, stats.samples, stats.successRate * 100,
                stats.meanMicros, stats.stddevMicros, stats.p90Micros, stats.p95Micros,
                stats.p99Micros);
    }

    private static void writeCsvRows(PrintWriter csv, BenchmarkResult result) {
        writeCsvRow(csv, result, result.contains);
        writeCsvRow(csv, result, result.add);
        writeCsvRow(csv, result, result.remove);
    }

    private static void writeCsvRow(PrintWriter csv, BenchmarkResult result, OperationStats stats) {
        if (stats == null) return;
        csv.printf(Locale.ROOT, "%s,%s,%s,%d,%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                result.implementation.csvName, result.workload, KEY_DISTRIBUTION, result.threadCount,
                stats.operation.csvName, stats.samples, stats.successRate, stats.meanMicros,
                stats.stddevMicros, stats.p90Micros, stats.p95Micros, stats.p99Micros,
                result.meanThroughput);
    }
}
