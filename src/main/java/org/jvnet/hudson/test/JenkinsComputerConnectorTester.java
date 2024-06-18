/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.slaves.ComputerConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Test bed to verify the configuration roundtripness of the {@link ComputerConnector}.
 *
 * @author Kohsuke Kawaguchi
 * @see HudsonTestCase#computerConnectorTester
 * @since 1.436
 */
public class JenkinsComputerConnectorTester extends AbstractDescribableImpl<JenkinsComputerConnectorTester> {
    public final JenkinsRule jenkinsRule;
    public ComputerConnector connector;

    public JenkinsComputerConnectorTester(JenkinsRule testCase) {
        this.jenkinsRule = testCase;
    }

    public void doConfigSubmit(StaplerRequest req) {
        JSONObject form;
        try {
            form = req.getSubmittedForm();
        } catch (Exception e) {
            // TODO stop wrapping once we drop support for EE 8
            throw new RuntimeException(e);
        }
        connector = req.bindJSON(ComputerConnector.class, form.getJSONObject("connector"));
    }

    public List<ComputerConnectorDescriptor> getConnectorDescriptors() {
        return ComputerConnectorDescriptor.all();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<JenkinsComputerConnectorTester> {}
}
