package jenkins.benchmark.jmh;


import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

/**
 * Find classes annotated with {@link JmhBenchmark} to run their benchmark methods.
 * @since 2.50
 */
@SuppressWarnings("WeakerAccess")
public final class BenchmarkFinder {
    private BenchmarkFinder() {
    }
    /**
     * Includes classes annotated with {@link JmhBenchmark} as candidates for JMH benchmarks.
     *
     * @param optionsBuilder the optionsBuilder used to build the benchmarks
     */
    public static void findBenchmarks(ChainedOptionsBuilder optionsBuilder) {
        for (IndexItem<JmhBenchmark, Object> item : Index.load(JmhBenchmark.class, Object.class)) {
            optionsBuilder.include(item.className() + item.annotation().value());
        }
    }
}
