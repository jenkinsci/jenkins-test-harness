package jenkins.benchmark.jmh.samples;

import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import org.openjdk.jmh.annotations.Benchmark;

import java.util.Objects;

/**
 * Sample benchmark without doing anything special to the Jenkins instance.
 */
@JmhBenchmark
public class JmhStateBenchmark {
    public static class MyState extends JmhBenchmarkState {
    }

    @Benchmark
    public void benchmark(MyState state) {
        Objects.requireNonNull(state.getJenkins());
    }
}
