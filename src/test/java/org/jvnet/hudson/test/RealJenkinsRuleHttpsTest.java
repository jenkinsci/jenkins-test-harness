package org.jvnet.hudson.test;

import java.io.IOException;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RealJenkinsRuleHttpsTest {
    private static final Logger LOGGER = Logger.getLogger(RealJenkinsRuleHttpsTest.class.getName());

    @Rule
    public final RealJenkinsRule rr = new RealJenkinsRule().https();

    @Rule
    public InboundAgentRule iar = new InboundAgentRule();

    @Before
    public void setUp() throws Throwable {
        rr.startJenkins();
    }

    @Test
    public void runningStepAndUsingHtmlUnit() throws Throwable {
        // We can run steps
        rr.runRemotely(RealJenkinsRuleHttpsTest::log);
        // replica1 directly
        try (var wc = rr.createWebClient()) {
            wc.getPage(rr.getUrl());
        }
    }

    @Test
    public void inboundAgent() throws Throwable {
        var options = InboundAgentRule.Options
                .newBuilder()
                .name("remote")
                .webSocket()
                .color(PrefixedOutputStream.Color.YELLOW);
        iar.createAgent(rr, options.build());
    }

    private static void log(JenkinsRule r) throws IOException {
        LOGGER.info("Running on " + r.getURL().toExternalForm());
    }
}
