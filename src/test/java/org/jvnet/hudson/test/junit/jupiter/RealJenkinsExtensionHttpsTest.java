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

package org.jvnet.hudson.test.junit.jupiter;

import java.io.IOException;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PrefixedOutputStream;

class RealJenkinsExtensionHttpsTest {
    private static final Logger LOGGER = Logger.getLogger(RealJenkinsExtensionHttpsTest.class.getName());

    @RegisterExtension
    private final RealJenkinsExtension extension = new RealJenkinsExtension().https();

    @RegisterExtension
    private final InboundAgentExtension iae = new InboundAgentExtension();

    @BeforeEach
    void setUp() throws Throwable {
        extension.startJenkins();
    }

    @Test
    void runningStepAndUsingHtmlUnit() throws Throwable {
        // We can run steps
        extension.runRemotely(RealJenkinsExtensionHttpsTest::log);
        // web client trusts the cert
        try (var wc = extension.createWebClient()) {
            wc.getPage(extension.getUrl());
        }
    }

    @Test
    void inboundAgent() throws Throwable {
        var options = InboundAgentExtension.Options.newBuilder()
                .name("remote")
                .webSocket()
                .color(PrefixedOutputStream.Color.YELLOW);
        iae.createAgent(extension, options.build());
    }

    private static void log(JenkinsRule r) throws IOException {
        LOGGER.info("Running on " + r.getURL().toExternalForm());
    }
}
