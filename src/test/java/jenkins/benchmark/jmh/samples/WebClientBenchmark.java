package jenkins.benchmark.jmh.samples;

import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import jenkins.benchmark.jmh.JmhJenkinsRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.openjdk.jmh.annotations.Benchmark;

@JmhBenchmark
public class WebClientBenchmark {
    public static class MyState extends JmhBenchmarkState {
        JenkinsRule.WebClient webClient = null;

        @Override
        public void setup() {
            JmhJenkinsRule jenkinsRule = new JmhJenkinsRule();
            getJenkins().setSecurityRealm(jenkinsRule.createDummySecurityRealm());
            webClient = jenkinsRule.createWebClient();
        }

        @Override
        public void tearDown() {
            webClient.close();
        }
    }

    @Benchmark
    public void benchmark(MyState state) throws Exception {
        state.webClient.goTo("");
    }
}
