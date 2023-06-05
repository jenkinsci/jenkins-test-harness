package jenkins.benchmark.jmh;


import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import org.jvnet.hudson.annotation_indexer.Index;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

/**
 * Find classes annotated with {@link JmhBenchmark} to run their benchmark methods.
 *
 * @since 2.50
 */
@SuppressWarnings("WeakerAccess")
public final class BenchmarkFinder {
    private final ClassLoader classLoader;

    /**
     * Class whose {@link ClassLoader} will be used to search for benchmarks.
     *
     * @param clazz the class whose {@link ClassLoader} will be used to search for benchmarks.
     */
    public BenchmarkFinder(Class<?> clazz) {
        this.classLoader = clazz.getClassLoader();
    }

    /**
     * Includes classes annotated with {@link JmhBenchmark} as candidates for JMH benchmarks.
     *
     * @param optionsBuilder the optionsBuilder used to build the benchmarks
     */
    public void findBenchmarks(ChainedOptionsBuilder optionsBuilder) throws IOException {
        for (AnnotatedElement e : Index.list(JmhBenchmark.class, classLoader)) {
            Class<?> clazz = (Class<?>) e;
            JmhBenchmark annotation = clazz.getAnnotation(JmhBenchmark.class);
            optionsBuilder.include(clazz.getName() + annotation.value());
        }
    }
}
