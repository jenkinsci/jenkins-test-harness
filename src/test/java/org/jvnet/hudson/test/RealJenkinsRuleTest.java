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
import hudson.model.Item;
import hudson.util.PluginServletFilter;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.recipes.LocalData;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.kohsuke.stapler.Stapler;

public class RealJenkinsRuleTest {

    // TODO addPlugins does not currently take effect when used inside test method
    @Rule public RealJenkinsRule rr = new RealJenkinsRule().addPlugins("plugins/structs.hpi");
    @Rule public RealJenkinsRule rr2 = new RealJenkinsRule().addPlugins("plugins/failure.hpi");

    @Test public void smokes() throws Throwable {
        rr.extraEnv("SOME_ENV_VAR", "value").extraEnv("NOT_SET", null).then(RealJenkinsRuleTest::_smokes);
    }
    private static void _smokes(JenkinsRule r) throws Throwable {
        System.err.println("running in: " + r.jenkins.getRootUrl());
        assertTrue(Main.isUnitTest);
        assertNotNull(r.jenkins.getPlugin("structs"));
        assertEquals("value", System.getenv("SOME_ENV_VAR"));
    }

    @Test public void testFilter() throws Throwable{
        rr.startJenkins();
        rr.runRemotely(RealJenkinsRuleTest::_testFilter);
        rr.runRemotely(RealJenkinsRuleTest::_htmlUnit1);
    }

    @Test public void testReturnObject() throws Throwable {
        rr.startJenkins();
        assertEquals(rr.getUrl().toExternalForm(), rr.runRemotely(RealJenkinsRuleTest::_getJenkinsUrlFromRemote));
    }

    private static void _testFilter(JenkinsRule jenkinsRule) throws Throwable{
        PluginServletFilter.addFilter(new Filter() {

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                String fake = request.getParameter("fake");
                chain.doFilter(request, response);
            }

            @Override
            public void destroy() {}

            @Override
            public void init(FilterConfig filterConfig) throws ServletException {}
        });
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

    private static String _getJenkinsUrlFromRemote(JenkinsRule r) {
        return r.jenkins.getRootUrl();
    }

    @LocalData
    @Test public void localData() throws Throwable {
        rr.then(RealJenkinsRuleTest::_localData);
    }
    private static void _localData(JenkinsRule r) throws Throwable {
        assertThat(r.jenkins.getItems().stream().map(Item::getName).toArray(), arrayContainingInAnyOrder("x"));
    }

    @Test public void restart() throws Throwable {
        rr.then(RealJenkinsRuleTest::_restart1);
        rr.then(RealJenkinsRuleTest::_restart2);
    }
    private static void _restart1(JenkinsRule r) throws Throwable {
        assertEquals(r.jenkins.getRootUrl(), r.getURL().toString());
        FileUtils.write(new File(r.jenkins.getRootDir(), "url.txt"), r.getURL().toString(), StandardCharsets.UTF_8);
    }
    private static void _restart2(JenkinsRule r) throws Throwable {
        assertEquals(r.jenkins.getRootUrl(), r.getURL().toString());
        assertEquals(r.jenkins.getRootUrl(), FileUtils.readFileToString(new File(r.jenkins.getRootDir(), "url.txt"), StandardCharsets.UTF_8));
    }

    @Test public void stepsDoNotRunOnHttpWorkerThread() throws Throwable {
        rr.then(RealJenkinsRuleTest::_stepsDoNotRunOnHttpWorkerThread);
    }
    private static void _stepsDoNotRunOnHttpWorkerThread(JenkinsRule r) throws Throwable {
        assertNull(Stapler.getCurrentRequest());
    }


    @Test
    public void test500Errors() throws IOException {
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(500);
        try {
            RealJenkinsRule.checkResult(conn);
            fail("expected exception JenkinsStartupException");
        }catch (RealJenkinsRule.JenkinsStartupException ae){

        }
    }
    @Test
    public void test503Errors() throws IOException {
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(503);
        when(conn.getErrorStream()).thenReturn(IOUtils.toInputStream("Jenkins Custom Error", "UTF-8"));

        Optional<String> s = RealJenkinsRule.checkResult(conn);

        assertThat(s, is(Optional.of("Jenkins Custom Error")));
    }

    @Test
    public void test200Ok() throws IOException {

        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(IOUtils.toInputStream("blah blah blah", "UTF-8"));

        Optional<String> s = RealJenkinsRule.checkResult(conn);

        verify(conn, times(1)).getInputStream();
        assertThat(s, is(Optional.empty()));
    }

    /**
     * plugins/failure.hpi
     *  Plugin that has this:
     *
     *  @Initializer(after=JOB_LOADED)
     *     public static void init() throws IOException {
     *         throw new IOException("oops");
     *     }
     *
     */
    @Test(expected = RealJenkinsRule.JenkinsStartupException.class)
    public void whenUsingFailurePlugin() throws Throwable {
            rr2.startJenkins();
    }

    // TODO interesting scenarios to test:
    // · throw an exception of a type defined in Jenkins code
    // · run with optional dependencies disabled

}
