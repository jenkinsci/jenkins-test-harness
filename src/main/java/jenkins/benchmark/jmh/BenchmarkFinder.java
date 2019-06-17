package jenkins.benchmark.jmh;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

/**
 * Find classes annotated with {@link JmhBenchmark} to run their benchmark methods.
 * @since TODO
 */
@SuppressWarnings("WeakerAccess")
public final class BenchmarkFinder {
    private final String[] packageNames;

    /**
     * Creates a {@link BenchmarkFinder}
     *
     * @param packageNames find benchmarks in these packages
     */
    public BenchmarkFinder(String... packageNames) {
        this.packageNames = packageNames;
    }

    /**
     * Includes classes annotated with {@link JmhBenchmark} as candidates for JMH benchmarks.
     *
     * @param optionsBuilder the optionsBuilder used to build the benchmarks
     */
    public void findBenchmarks(ChainedOptionsBuilder optionsBuilder) {
        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableAnnotationInfo()
                             .whitelistPackages(packageNames)
                             .scan()) {
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(JmhBenchmark.class.getName())) {
                Class<?> clazz = classInfo.loadClass();
                JmhBenchmark annotation = clazz.getAnnotation(JmhBenchmark.class);
                assert annotation != null;
                optionsBuilder.include(clazz.getName() + annotation.value());
            }
        }
    }
}
