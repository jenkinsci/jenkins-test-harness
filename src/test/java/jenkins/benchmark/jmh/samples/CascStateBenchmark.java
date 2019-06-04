package jenkins.benchmark.jmh.samples;

import jenkins.benchmark.jmh.casc.CascJmhBenchmarkState;
import jenkins.benchmark.jmh.JmhBenchmark;
import org.openjdk.jmh.annotations.Benchmark;

import javax.annotation.Nonnull;

import static org.junit.Assert.assertEquals;

/**
 * Sample benchmark configuring Jenkins instance using Configuration as Code
 */
@JmhBenchmark
public class CascStateBenchmark {
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
