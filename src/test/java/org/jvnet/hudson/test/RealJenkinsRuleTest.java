/*
 * The MIT License
 *
 * Copyright 2021 CloudBees, Inc.
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

import hudson.Launcher;
import hudson.Main;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import jenkins.model.Jenkins;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;

public class RealJenkinsRuleTest {

    @Rule public RealJenkinsRule rr = new RealJenkinsRule().addPlugins("structs.hpi");

    @Test public void smokes() throws Throwable {
        rr.then(RealJenkinsRuleTest::_smokes);
    }
    private static void _smokes(JenkinsRule r) throws Throwable {
        System.err.println("running in: " + r.jenkins.getRootUrl());
        assertTrue(Main.isUnitTest);
        assertNotNull(r.jenkins.getPlugin("structs"));
    }

    @Test public void error() {
        boolean erred = false;
        try {
            rr.then(RealJenkinsRuleTest::_error);
        } catch (Throwable t) {
            erred = true;
            t.printStackTrace();
            assertEquals("java.lang.AssertionError: oops", t.toString());
        }
        assertTrue(erred);
    }
    private static void _error(JenkinsRule r) throws Throwable {
        assert false: "oops";
    }

    @Test public void agentBuild() throws Throwable {
        rr.then(RealJenkinsRuleTest::_agentBuild);
    }
    private static void _agentBuild(JenkinsRule r) throws Throwable {
        FreeStyleProject p = r.createFreeStyleProject();
        AtomicReference<Boolean> ran = new AtomicReference<>(false);
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                ran.set(true);
                return true;
            }
        });
        p.setAssignedNode(r.createOnlineSlave());
        r.buildAndAssertSuccess(p);
        assertTrue(ran.get());
    }

    @Test public void htmlUnit() throws Throwable {
        rr.startJenkins();
        rr.runRemotely(RealJenkinsRuleTest::_htmlUnit1);
        System.err.println("running against " + rr.getUrl());
        rr.runRemotely(RealJenkinsRuleTest::_htmlUnit2);
    }
    private static void _htmlUnit1(JenkinsRule r) throws Throwable {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        FreeStyleProject p = r.createFreeStyleProject("p");
        p.setDescription("hello");
    }
    private static void _htmlUnit2(JenkinsRule r) throws Throwable {
        FreeStyleProject p = r.jenkins.getItemByFullName("p", FreeStyleProject.class);
        r.submit(r.createWebClient().login("admin").getPage(p, "configure").getFormByName("config"));
        assertEquals("hello", p.getDescription());
    }

    // TODO interesting scenarios to test:
    // · throw an exception of a type defined in Jenkins code
    // · run with optional dependencies disabled

}
