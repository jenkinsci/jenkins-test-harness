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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.Functions;
import hudson.Launcher;
import hudson.Main;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import hudson.util.PluginServletFilter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.Stapler;

public class RealJenkinsRuleTest {

    // TODO addPlugins does not currently take effect when used inside test method
    @Rule public RealJenkinsRule rr = new RealJenkinsRule().addPlugins("plugins/structs.hpi").withDebugPort(4001).withDebugServer(false);
    @Rule public RealJenkinsRule rrWithFailure = new RealJenkinsRule().addPlugins("plugins/failure.hpi");

    @Test public void smokes() throws Throwable {
        rr.extraEnv("SOME_ENV_VAR", "value").extraEnv("NOT_SET", null).withLogger(Jenkins.class, Level.FINEST).then(RealJenkinsRuleTest::_smokes);
    }
    private static void _smokes(JenkinsRule r) throws Throwable {
        System.err.println("running in: " + r.jenkins.getRootUrl());
        assertTrue(Main.isUnitTest);
        assertNotNull(r.jenkins.getPlugin("structs"));
        assertEquals("value", System.getenv("SOME_ENV_VAR"));
    }

    @Test public void testReturnObject() throws Throwable {
        rr.startJenkins();
        assertEquals(rr.getUrl().toExternalForm(), rr.runRemotely(RealJenkinsRuleTest::_getJenkinsUrlFromRemote));
    }

    @Test public void testThrowsException() {
        assertThat(assertThrows(RealJenkinsRule.StepException.class, () -> rr.then(RealJenkinsRuleTest::throwsException)).getMessage(),
            containsString("IllegalStateException: something is wrong"));
    }

    @Test public void killedExternally() throws Throwable {
        rr.startJenkins();
        try {
            rr.proc.destroy();
        } finally {
            assertThrows("nonzero exit code: 143", AssertionError.class, () -> rr.stopJenkins());
        }
    }

    private static void throwsException(JenkinsRule r) throws Throwable {
        throw new IllegalStateException("something is wrong");
    }

    @Test public void testFilter() throws Throwable{
        rr.startJenkins();
        rr.runRemotely(RealJenkinsRuleTest::_testFilter1);
        // Now run another step, body irrelevant just making sure it is not broken
        // (do *not* combine into one runRemotely call):
        rr.runRemotely(RealJenkinsRuleTest::_testFilter2);
    }
    private static void _testFilter1(JenkinsRule jenkinsRule) throws Throwable {
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
    private static void _testFilter2(JenkinsRule jenkinsRule) throws Throwable {}

    @Test public void chainedSteps() throws Throwable {
        rr.startJenkins();
        rr.runRemotely(RealJenkinsRuleTest::chainedSteps1, RealJenkinsRuleTest::chainedSteps2);
    }
    private static void chainedSteps1(JenkinsRule jenkinsRule) throws Throwable {
        System.setProperty("key", "xxx");
    }
    private static void chainedSteps2(JenkinsRule jenkinsRule) throws Throwable {
        assertEquals("xxx", System.getProperty("key"));
    }

    @Test public void error() {
        boolean erred = false;
        try {
            rr.then(RealJenkinsRuleTest::_error);
        } catch (Throwable t) {
            erred = true;
            t.printStackTrace();
            assertThat(Functions.printThrowable(t), containsString("java.lang.AssertionError: oops"));
        }
        assertTrue(erred);
    }
    private static void _error(JenkinsRule r) throws Throwable {
        assert false: "oops";
    }

    @Test public void agentBuild() throws Throwable {
        try (TailLog tailLog = new TailLog(rr, "p", 1).withColor(PrefixedOutputStream.Color.MAGENTA)) {
            rr.then(RealJenkinsRuleTest::_agentBuild);
            tailLog.waitForCompletion();
        }
    }
    private static void _agentBuild(JenkinsRule r) throws Throwable {
        FreeStyleProject p = r.createFreeStyleProject("p");
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
        Files.writeString(r.jenkins.getRootDir().toPath().resolve("url.txt"), r.getURL().toString(), StandardCharsets.UTF_8);
        r.jenkins.getExtensionList(ItemListener.class).add(0, new ShutdownListener());
    }
    private static void _restart2(JenkinsRule r) throws Throwable {
        assertEquals(r.jenkins.getRootUrl(), r.getURL().toString());
        assertEquals(r.jenkins.getRootUrl(), Files.readString(r.jenkins.getRootDir().toPath().resolve("url.txt"), StandardCharsets.UTF_8));
        assertTrue(new File(Jenkins.get().getRootDir(), "RealJenkinsRule-ran-cleanUp").exists());
    }
    private static class ShutdownListener extends ItemListener {
        private final String fileName = "RealJenkinsRule-ran-cleanUp";
        @Override
        public void onBeforeShutdown() {
            try {
                new File(Jenkins.get().getRootDir(), fileName).createNewFile();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Test public void stepsDoNotRunOnHttpWorkerThread() throws Throwable {
        rr.then(RealJenkinsRuleTest::_stepsDoNotRunOnHttpWorkerThread);
    }
    private static void _stepsDoNotRunOnHttpWorkerThread(JenkinsRule r) throws Throwable {
        assertNull(Stapler.getCurrentRequest());
    }

    @Test public void stepsDoNotOverwriteJenkinsLocationConfigurationIfOtherwiseSet() throws Throwable {
        rr.then(RealJenkinsRuleTest::_stepsDoNotOverwriteJenkinsLocationConfigurationIfOtherwiseSet1);
        rr.then(RealJenkinsRuleTest::_stepsDoNotOverwriteJenkinsLocationConfigurationIfOtherwiseSet2);
    }
    private static void _stepsDoNotOverwriteJenkinsLocationConfigurationIfOtherwiseSet1(JenkinsRule r) throws Throwable {
        assertNotNull(JenkinsLocationConfiguration.get().getUrl());
        JenkinsLocationConfiguration.get().setUrl("https://example.com/");
    }
    private static void _stepsDoNotOverwriteJenkinsLocationConfigurationIfOtherwiseSet2(JenkinsRule r) throws Throwable {
        assertEquals("https://example.com/", JenkinsLocationConfiguration.get().getUrl());
    }

    @Test
    public void test500Errors() throws IOException {
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(500);
        assertThrows(RealJenkinsRule.JenkinsStartupException.class,
                     () -> RealJenkinsRule.checkResult(conn));
    }
    @Test
    public void test503Errors() throws IOException {
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(503);
        when(conn.getErrorStream()).thenReturn(new ByteArrayInputStream("Jenkins Custom Error".getBytes(StandardCharsets.UTF_8)));

        String s = RealJenkinsRule.checkResult(conn);

        assertThat(s, is("Jenkins Custom Error"));
    }

    @Test
    public void test200Ok() throws IOException {

        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream()).thenReturn(new ByteArrayInputStream("blah blah blah".getBytes(StandardCharsets.UTF_8)));

        String s = RealJenkinsRule.checkResult(conn);

        verify(conn, times(1)).getInputStream();
        assertThat(s, nullValue());
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
    @Test
    public void whenUsingFailurePlugin() throws Throwable {
        RealJenkinsRule.JenkinsStartupException jse = assertThrows(
                RealJenkinsRule.JenkinsStartupException.class, () -> rrWithFailure.startJenkins());
        assertThat(jse.getMessage(), containsString("Error</h1><pre>java.io.IOException: oops"));
    }

    // TODO interesting scenarios to test:
    // · throw an exception of a type defined in Jenkins code
    // · run with optional dependencies disabled

}
