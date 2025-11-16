/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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
        // web client trusts the cert
        try (var wc = rr.createWebClient()) {
            wc.getPage(rr.getUrl());
        }
    }

    @Test
    public void inboundAgent() throws Throwable {
        var options = InboundAgentRule.Options.newBuilder()
                .name("remote")
                .webSocket()
                .color(PrefixedOutputStream.Color.YELLOW);
        iar.createAgent(rr, options.build());
    }

    private static void log(JenkinsRule r) throws IOException {
        LOGGER.info("Running on " + r.getURL().toExternalForm());
    }
}
