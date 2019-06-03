package jenkins.jmh;

import org.junit.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

public class BenchmarkTest {
    @Test
    public void testJmhBenchmarks() throws Exception {
        ChainedOptionsBuilder optionsBuilder =
                new OptionsBuilder()
                        .forks(1)
                        .warmupForks(1)
                        .warmupBatchSize(1)
                        .shouldFailOnError(true)
                        .result("jmh-benchmark-report.json")
                        .timeUnit(TimeUnit.MICROSECONDS)
                        .resultFormat(ResultFormatType.JSON);
        BenchmarkFinder finder = new BenchmarkFinder(this.getClass().getPackage().getName());
        finder.findBenchmarks(optionsBuilder);
        new Runner(optionsBuilder.build()).run();
    }
}
