package jenkins.jmh.benchmarks;

import jenkins.jmh.JmhBenchmark;
import jenkins.jmh.casc.CascJmhBenchmarkState;
import org.openjdk.jmh.annotations.Benchmark;

import javax.annotation.Nonnull;

import static org.junit.Assert.assertEquals;

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
        assertEquals(state.getJenkins().getSystemMessage(),
                "Benchmark started with Configuration as Code");
        assertEquals(state.getJenkins().getNumExecutors(), 22);
    }
}
