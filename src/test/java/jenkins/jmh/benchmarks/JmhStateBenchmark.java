package jenkins.jmh.benchmarks;

import jenkins.jmh.JmhBenchmark;
import jenkins.jmh.JmhBenchmarkState;
import org.openjdk.jmh.annotations.Benchmark;

import java.util.Objects;

@JmhBenchmark
public class JmhStateBenchmark {
    public static class MyState extends JmhBenchmarkState {
    }

    @Benchmark
    public void benchmark(MyState state) {
        Objects.requireNonNull(state.getJenkins());
    }
}
