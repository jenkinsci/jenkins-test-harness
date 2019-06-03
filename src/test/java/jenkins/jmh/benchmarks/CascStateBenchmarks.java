package jenkins.jmh.benchmarks;

import jenkins.jmh.JmhBenchmark;
import jenkins.jmh.casc.CascJmhBenchmarkState;
import org.openjdk.jmh.annotations.Benchmark;

import javax.annotation.Nonnull;
import java.util.Objects;

@JmhBenchmark
public class CascStateBenchmarks {
    public static class MyState extends CascJmhBenchmarkState {
        @Nonnull
        @Override
        protected String getResourcePath() {
            return "sample-config.yaml";
        }
    }

    @Benchmark
    public void benchmark(MyState state) {
        Objects.requireNonNull(state.getJenkins());
    }
}
