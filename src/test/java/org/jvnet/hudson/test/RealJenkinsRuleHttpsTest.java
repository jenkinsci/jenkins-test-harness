package org.jvnet.hudson.test;

import java.io.IOException;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;

public class RealJenkinsRuleHttpsTest {
    private static final Logger LOGGER = Logger.getLogger(RealJenkinsRuleHttpsTest.class.getName());

    @Rule
    public final RealJenkinsRule rr = new RealJenkinsRule().https();

    @Test
    public void runningStepAndUsingHtmlUnit() throws Throwable {
        rr.startJenkins();
        // We can run steps
        rr.runRemotely(RealJenkinsRuleHttpsTest::log);
        // replica1 directly
        try (var wc = rr.createWebClient()) {
            wc.getPage(rr.getUrl());
        }
    }

    private static void log(JenkinsRule r) throws IOException {
        LOGGER.info("Running on " + r.getURL().toExternalForm());
    }
}
