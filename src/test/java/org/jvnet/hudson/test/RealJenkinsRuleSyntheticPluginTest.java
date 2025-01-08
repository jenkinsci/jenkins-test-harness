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

import java.util.logging.Level;
import jenkins.model.Jenkins;
import jenkins.security.ClassFilterImpl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.sample.plugin.CustomJobProperty;
import org.jvnet.hudson.test.sample.plugin.Stuff;

public final class RealJenkinsRuleSyntheticPluginTest {

    @Rule public RealJenkinsRule rr = new RealJenkinsRule().prepareHomeLazily(true);

    @Test public void smokes() throws Throwable {
        rr.addSyntheticPlugin(Stuff.class.getPackage()).done();
        rr.then(RealJenkinsRuleSyntheticPluginTest::_smokes);
    }

    private static void _smokes(JenkinsRule r) throws Throwable {
        assertThat(r.createWebClient().goTo("stuff", "text/plain").getWebResponse().getContentAsString(),
            is(Jenkins.get().getLegacyInstanceId()));
    }

    @Test public void classFilter() throws Throwable {
        rr.addSyntheticPlugin(CustomJobProperty.class.getPackage()).done().withLogger(ClassFilterImpl.class, Level.FINE);
        rr.then(r -> {
            var p = r.createFreeStyleProject();
            p.addProperty(new CustomJobProperty("expected in XML"));
            p.save();
            assertThat(p.getConfigFile().asString(), containsString("expected in XML"));
        });
    }

}
