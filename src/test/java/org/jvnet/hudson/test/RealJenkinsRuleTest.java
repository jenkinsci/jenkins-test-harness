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
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.Functions;
import hudson.Launcher;
import hudson.Main;
import hudson.PluginWrapper;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.JobProperty;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.listeners.ItemListener;
import hudson.util.PluginServletFilter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.AssumptionViolatedException;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.Stapler;

public class RealJenkinsRuleTest {

    @Rule public RealJenkinsRule rr = new RealJenkinsRule().prepareHomeLazily(true).withDebugPort(4001).withDebugServer(false);

    @Test public void smokes() throws Throwable {
        rr.addPlugins("plugins/structs.hpi");
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

    @Test public void customPrefix() throws Throwable {
        rr.withPrefix("/foo").startJenkins();
        assertThat(rr.getUrl().getPath(), equalTo("/foo/"));
    }

    @Test public void noPrefix() throws Throwable {
        rr.noPrefix().startJenkins();
        assertThat(rr.getUrl().getPath(), emptyString());
    }

    @Test public void ipv6() throws Throwable {
        // Use -Djava.net.preferIPv6Addresses=true if dualstack
        assumeThat(InetAddress.getLoopbackAddress(), instanceOf(Inet6Address.class));
        rr.withHost("::1").startJenkins();
        var externalForm = rr.getUrl().toExternalForm();
        assertEquals(externalForm, rr.runRemotely(RealJenkinsRuleTest::_getJenkinsUrlFromRemote));
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
        try (var tailLog = new TailLog(rr, "p", 1).withColor(PrefixedOutputStream.Color.MAGENTA)) {
            rr.then(r -> {
                var p = r.createFreeStyleProject("p");
                var ran = new AtomicBoolean();
                p.getBuildersList().add(TestBuilder.of((build, launcher, listener) -> ran.set(true)));
                p.setAssignedNode(r.createOnlineSlave());
                r.buildAndAssertSuccess(p);
                assertTrue(ran.get());
            });
            tailLog.waitForCompletion();
        }
    }

    @Test public void htmlUnit() throws Throwable {
        rr.startJenkins();
        rr.runRemotely(r -> {
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
            var p = r.createFreeStyleProject("p");
            p.setDescription("hello");
        });
        System.err.println("running against " + rr.getUrl());
        rr.runRemotely(r -> {
            var p = r.jenkins.getItemByFullName("p", FreeStyleProject.class);
            r.submit(r.createWebClient().login("admin").getPage(p, "configure").getFormByName("config"));
            assertEquals("hello", p.getDescription());
        });
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
        rr.then(r -> {
            assertEquals(r.jenkins.getRootUrl(), r.getURL().toString());
            Files.writeString(r.jenkins.getRootDir().toPath().resolve("url.txt"), r.getURL().toString(), StandardCharsets.UTF_8);
            r.jenkins.getExtensionList(ItemListener.class).add(0, new ShutdownListener());
        });
        rr.then(r -> {
            assertEquals(r.jenkins.getRootUrl(), r.getURL().toString());
            assertEquals(r.jenkins.getRootUrl(), Files.readString(r.jenkins.getRootDir().toPath().resolve("url.txt"), StandardCharsets.UTF_8));
            assertTrue(new File(Jenkins.get().getRootDir(), "RealJenkinsRule-ran-cleanUp").exists());
        });
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
        rr.then(r -> {
            assertNotNull(JenkinsLocationConfiguration.get().getUrl());
            JenkinsLocationConfiguration.get().setUrl("https://example.com/");
        });
        rr.then(r -> {
            assertEquals("https://example.com/", JenkinsLocationConfiguration.get().getUrl());
        });
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
                RealJenkinsRule.JenkinsStartupException.class, () -> rr.addPlugins("plugins/failure.hpi").startJenkins());
        assertThat(jse.getMessage(), containsString("Error</h1><pre>java.io.IOException: oops"));
    }

    @Test
    public void whenUsingWrongJavaHome() throws Throwable {
        IOException ex = assertThrows(
                IOException.class, () -> rr.withJavaHome("/noexists").startJenkins());
        assertThat(ex.getMessage(), containsString(File.separator + "noexists" + File.separator + "bin" + File.separator + "java"));
    }

    @Test 
    public void smokesJavaHome() throws Throwable {
        String altJavaHome = System.getProperty("java.home");
        rr.addPlugins("plugins/structs.hpi");
        rr.extraEnv("SOME_ENV_VAR", "value").extraEnv("NOT_SET", null).withJavaHome(altJavaHome).withLogger(Jenkins.class, Level.FINEST).then(RealJenkinsRuleTest::_smokes);
    }

    @Issue("https://github.com/jenkinsci/jenkins-test-harness/issues/359")
    @Test
    public void assumptions() throws Throwable {
        assertThat(assertThrows(AssumptionViolatedException.class, () -> rr.then(RealJenkinsRuleTest::_assumptions1)).getMessage(), is("got: <4>, expected: is <5>"));
        assertThat(assertThrows(AssumptionViolatedException.class, () -> rr.then(RealJenkinsRuleTest::_assumptions2)).getMessage(), is("oops: got: <4>, expected: is <5>"));
    }

    private static void _assumptions1(JenkinsRule r) throws Throwable {
        assumeThat(2 + 2, is(5));
    }

    private static void _assumptions2(JenkinsRule r) throws Throwable {
        assumeThat("oops", 2 + 2, is(5));
    }

    @Test
    public void timeoutDuringStep() throws Throwable {
        rr.withTimeout(10);
        assertThat(Functions.printThrowable(assertThrows(RealJenkinsRule.StepException.class, () -> rr.then(RealJenkinsRuleTest::hangs))),
            containsString("\tat " + RealJenkinsRuleTest.class.getName() + ".hangs(RealJenkinsRuleTest.java:"));
    }

    private static void hangs(JenkinsRule r) throws Throwable {
        System.err.println("Hanging step…");
        Thread.sleep(Long.MAX_VALUE);
    }

    @Test
    public void noDetachedPlugins() throws Throwable {
        // we should be the only plugin in Jenkins.
        rr.then(RealJenkinsRuleTest::_noDetachedPlugins);
    }

    private static void _noDetachedPlugins(JenkinsRule r) throws Throwable {
        // only RealJenkinsRuleInit should be present
        List<PluginWrapper> plugins = r.jenkins.getPluginManager().getPlugins();
        assertThat(plugins, hasSize(1));
        assertThat(plugins.get(0).getShortName(), is("RealJenkinsRuleInit"));
    }

    @Test
    public void safeExit() throws Throwable {
        rr.then(r -> {
            var p = r.createFreeStyleProject();
            p.getBuildersList().add(TestBuilder.of((build, launcher, listener) -> Thread.sleep(Long.MAX_VALUE)));
            p.scheduleBuild2(0).waitForStart();
        });
    }

    @Test public void xStreamSerializable() throws Throwable {
        rr.startJenkins();
        // Neither ParametersDefinitionProperty nor ParametersAction could be passed directly.
        // (In this case, ParameterDefinition and ParameterValue could have been used raw.
        // But even List<ParameterValue> cannot be typed here, only e.g. ArrayList<ParameterValue>.)
        var prop = XStreamSerializable.of(new ParametersDefinitionProperty(new StringParameterDefinition("X", "dflt")));
        // Static method handle idiom:
        assertThat(rr.runRemotely(RealJenkinsRuleTest::_xStreamSerializable, prop).object().getAllParameters(), hasSize(1));
        // Lambda idiom:
        assertThat(rr.runRemotely(r -> {
            var p = r.createFreeStyleProject();
            p.addProperty(prop.object());
            var b = r.buildAndAssertSuccess(p);
            return XStreamSerializable.of(b.getAction(ParametersAction.class));
        }).object().getAllParameters(), hasSize(1));
    }

    private static XStreamSerializable<ParametersAction> _xStreamSerializable(JenkinsRule r, XStreamSerializable<? extends JobProperty<? super FreeStyleProject>> prop) throws Throwable {
        var p = r.createFreeStyleProject();
        p.addProperty(prop.object());
        var b = r.buildAndAssertSuccess(p);
        return XStreamSerializable.of(b.getAction(ParametersAction.class));
    }

    @Ignore("inner class inside lambda breaks with an opaque NotSerializableException: RealJenkinsRuleTest; use TestBuilder.of instead")
    @Test public void lambduh() throws Throwable {
        rr.then(r -> {
            r.createFreeStyleProject().getBuildersList().add(new TestBuilder() {
                @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                    return true;
                }
            });
        });
    }

}
