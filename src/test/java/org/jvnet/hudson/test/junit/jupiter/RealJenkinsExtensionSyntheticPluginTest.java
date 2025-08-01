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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.logging.Level;
import jenkins.model.Jenkins;
import jenkins.security.ClassFilterImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.sample.plugin.CustomJobProperty;
import org.jvnet.hudson.test.sample.plugin.Stuff;

class RealJenkinsExtensionSyntheticPluginTest {

    @RegisterExtension
    private final RealJenkinsExtension extension = new RealJenkinsExtension().prepareHomeLazily(true);

    @Test
    void smokes() throws Throwable {
        extension.addSyntheticPlugin(new RealJenkinsExtension.SyntheticPlugin(Stuff.class));
        extension.then(RealJenkinsExtensionSyntheticPluginTest::_smokes);
    }

    private static void _smokes(JenkinsRule r) throws Throwable {
        assertThat(
                r.createWebClient().goTo("stuff", "text/plain").getWebResponse().getContentAsString(),
                is(Jenkins.get().getLegacyInstanceId()));
    }

    @Test
    void classFilter() throws Throwable {
        extension
                .addSyntheticPlugin(new RealJenkinsExtension.SyntheticPlugin(CustomJobProperty.class))
                .withLogger(ClassFilterImpl.class, Level.FINE);
        extension.then(r -> {
            var p = r.createFreeStyleProject();
            p.addProperty(new CustomJobProperty("expected in XML"));
            assertThat(p.getConfigFile().asString(), containsString("expected in XML"));
        });
    }

    @Test
    void dynamicLoad() throws Throwable {
        var pluginJpi = extension.createSyntheticPlugin(new RealJenkinsExtension.SyntheticPlugin(Stuff.class));
        extension.then(r -> {
            r.jenkins.pluginManager.dynamicLoad(pluginJpi);
            assertThat(
                    r.createWebClient()
                            .goTo("stuff", "text/plain")
                            .getWebResponse()
                            .getContentAsString(),
                    is(Jenkins.get().getLegacyInstanceId()));
        });
    }
}
