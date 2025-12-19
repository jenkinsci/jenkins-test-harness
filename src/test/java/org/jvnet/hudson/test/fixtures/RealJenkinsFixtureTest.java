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

package org.jvnet.hudson.test.fixtures;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.TailLog;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.XStreamSerializable;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.Stapler;
import org.opentest4j.TestAbortedException;

class RealJenkinsFixtureTest {

    private final RealJenkinsFixture fixture =
            new RealJenkinsFixture().prepareHomeLazily(true).withDebugPort(4001).withDebugServer(false);

    @BeforeEach
    void beforeEach(TestInfo info) throws Exception {
        fixture.setUp(info.getTestMethod().orElseThrow(), null);
    }

    @AfterEach
    void afterEach() throws Exception {
        fixture.tearDown();
    }

    @Test
    void smokes(TestInfo info) throws Throwable {
        fixture.addPlugins("plugins/structs.hpi");
        fixture.extraEnv("SOME_ENV_VAR", "value")
                .extraEnv("NOT_SET", null)
                .withLogger(Jenkins.class, Level.FINEST)
                .then(info.getTestMethod().orElseThrow(), null, RealJenkinsFixtureTest::_smokes);
    }

    private static void _smokes(JenkinsRule r) throws Throwable {
        System.err.println("running in: " + r.jenkins.getRootUrl());
        assertTrue(Main.isUnitTest);
        assertNotNull(r.jenkins.getPlugin("structs"));
        assertEquals("value", System.getenv("SOME_ENV_VAR"));
    }

    @Test
    void testReturnObject(TestInfo info) throws Throwable {
        fixture.startJenkins(info.getTestMethod().orElseThrow(), null);
        assertThatLocalAndRemoteUrlEquals();
    }

    @Test
    void customPrefix(TestInfo info) throws Throwable {
        fixture.withPrefix("/foo").startJenkins(info.getTestMethod().orElseThrow(), null);
        assertThat(fixture.getUrl().getPath(), equalTo("/foo/"));
        assertThatLocalAndRemoteUrlEquals();
        fixture.runRemotely(r -> {
            assertThat(r.contextPath, equalTo("/foo"));
        });
    }

    @Test
    void complexPrefix(TestInfo info) throws Throwable {
        fixture.withPrefix("/foo/bar").startJenkins(info.getTestMethod().orElseThrow(), null);
        assertThat(fixture.getUrl().getPath(), equalTo("/foo/bar/"));
        assertThatLocalAndRemoteUrlEquals();
        fixture.runRemotely(r -> {
            assertThat(r.contextPath, equalTo("/foo/bar"));
        });
    }

    @Test
    void noPrefix(TestInfo info) throws Throwable {
        fixture.withPrefix("").startJenkins(info.getTestMethod().orElseThrow(), null);
        assertThat(fixture.getUrl().getPath(), equalTo("/"));
        assertThatLocalAndRemoteUrlEquals();
        fixture.runRemotely(r -> {
            assertThat(r.contextPath, equalTo(""));
        });
    }

    @Test
    void invalidPrefixes() {
        assertThrows(IllegalArgumentException.class, () -> fixture.withPrefix("foo"));
        assertThrows(IllegalArgumentException.class, () -> fixture.withPrefix("/foo/"));
    }

    @Test
    void ipv6(TestInfo info) throws Throwable {
        // Use -Djava.net.preferIPv6Addresses=true if dualstack
        assumeTrue(InetAddress.getLoopbackAddress() instanceof Inet6Address);
        fixture.withHost("::1").startJenkins(info.getTestMethod().orElseThrow(), null);
        assertThatLocalAndRemoteUrlEquals();
    }

    private void assertThatLocalAndRemoteUrlEquals() throws Throwable {
        assertEquals(
                fixture.getUrl().toExternalForm(),
                fixture.runRemotely(RealJenkinsFixtureTest::_getJenkinsUrlFromRemote));
    }

    @Test
    void testThrowsException(TestInfo info) {
        assertThat(
                assertThrows(
                                RealJenkinsFixture.StepException.class,
                                () -> fixture.then(
                                        info.getTestMethod().orElseThrow(),
                                        null,
                                        RealJenkinsFixtureTest::throwsException))
                        .getMessage(),
                containsString("IllegalStateException: something is wrong"));
    }

    @Test
    void killedExternally(TestInfo info) throws Exception {
        fixture.startJenkins(info.getTestMethod().orElseThrow(), null);
        try {
            fixture.getProcess().destroy();
        } finally {
            assertThrows(AssertionError.class, fixture::stopJenkins, "nonzero exit code: 143");
        }
    }

    private static void throwsException(JenkinsRule r) throws Throwable {
        throw new IllegalStateException("something is wrong");
    }

    @Test
    void testFilter(TestInfo info) throws Throwable {
        fixture.startJenkins(info.getTestMethod().orElseThrow(), null);
        fixture.runRemotely(RealJenkinsFixtureTest::_testFilter1);
        // Now run another step, body irrelevant just making sure it is not broken
        // (do *not* combine into one runRemotely call):
        fixture.runRemotely(RealJenkinsFixtureTest::_testFilter2);
    }

    private static void _testFilter1(JenkinsRule jenkinsRule) throws Throwable {
        PluginServletFilter.addFilter(new Filter() {

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
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

    @Test
    void chainedSteps(TestInfo info) throws Throwable {
        fixture.startJenkins(info.getTestMethod().orElseThrow(), null);
        fixture.runRemotely(RealJenkinsFixtureTest::chainedSteps1, RealJenkinsFixtureTest::chainedSteps2);
    }

    private static void chainedSteps1(JenkinsRule jenkinsRule) throws Throwable {
        System.setProperty("key", "xxx");
    }

    private static void chainedSteps2(JenkinsRule jenkinsRule) throws Throwable {
        assertEquals("xxx", System.getProperty("key"));
    }

    @Test
    void error(TestInfo info) {
        boolean erred = false;
        try {
            fixture.then(info.getTestMethod().orElseThrow(), null, RealJenkinsFixtureTest::_error);
        } catch (Throwable t) {
            erred = true;
            t.printStackTrace();
            assertThat(Functions.printThrowable(t), containsString("java.lang.AssertionError: oops"));
        }
        assertTrue(erred);
    }

    private static void _error(JenkinsRule r) throws Throwable {
        assert false : "oops";
    }

    @Test
    void agentBuild(TestInfo info) throws Throwable {
        try (var tailLog = new TailLog(fixture, "p", 1).withColor(PrefixedOutputStream.Color.MAGENTA)) {
            fixture.then(info.getTestMethod().orElseThrow(), null, r -> {
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

    @Test
    void htmlUnit(TestInfo info) throws Throwable {
        fixture.startJenkins(info.getTestMethod().orElseThrow(), null);
        fixture.runRemotely(r -> {
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                    .grant(Jenkins.ADMINISTER)
                    .everywhere()
                    .to("admin"));
            var p = r.createFreeStyleProject("p");
            p.setDescription("hello");
        });
        System.err.println("running against " + fixture.getUrl());
        fixture.runRemotely(r -> {
            var p = r.jenkins.getItemByFullName("p", FreeStyleProject.class);
            r.submit(r.createWebClient().login("admin").getPage(p, "configure").getFormByName("config"));
            assertEquals("hello", p.getDescription());
        });
    }

    private static String _getJenkinsUrlFromRemote(JenkinsRule r) {
        return r.jenkins.getRootUrl();
    }

    @LocalData
    @Test
    void localData(TestInfo info) throws Throwable {
        fixture.then(info.getTestMethod().orElseThrow(), "", RealJenkinsFixtureTest::_localData);
    }

    private static void _localData(JenkinsRule r) throws Throwable {
        assertThat(r.jenkins.getItems().stream().map(Item::getName).toArray(), arrayContainingInAnyOrder("x"));
    }

    @Test
    void restart(TestInfo info) throws Throwable {
        fixture.then(info.getTestMethod().orElseThrow(), null, r -> {
            assertEquals(r.jenkins.getRootUrl(), r.getURL().toString());
            Files.writeString(
                    r.jenkins.getRootDir().toPath().resolve("url.txt"),
                    r.getURL().toString(),
                    StandardCharsets.UTF_8);
            r.jenkins.getExtensionList(ItemListener.class).add(0, new ShutdownListener());
        });
        fixture.then(info.getTestMethod().orElseThrow(), null, r -> {
            assertEquals(r.jenkins.getRootUrl(), r.getURL().toString());
            assertEquals(
                    r.jenkins.getRootUrl(),
                    Files.readString(r.jenkins.getRootDir().toPath().resolve("url.txt"), StandardCharsets.UTF_8));
            assertTrue(new File(Jenkins.get().getRootDir(), "RealJenkinsExtension-ran-cleanUp").exists());
        });
    }

    private static class ShutdownListener extends ItemListener {
        private final String fileName = "RealJenkinsExtension-ran-cleanUp";

        @Override
        public void onBeforeShutdown() {
            try {
                new File(Jenkins.get().getRootDir(), fileName).createNewFile();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Test
    void stepsDoNotRunOnHttpWorkerThread(TestInfo info) throws Throwable {
        fixture.then(
                info.getTestMethod().orElseThrow(), null, RealJenkinsFixtureTest::_stepsDoNotRunOnHttpWorkerThread);
    }

    private static void _stepsDoNotRunOnHttpWorkerThread(JenkinsRule r) throws Throwable {
        assertNull(Stapler.getCurrentRequest());
    }

    @Test
    void stepsDoNotOverwriteJenkinsLocationConfigurationIfOtherwiseSet(TestInfo info) throws Throwable {
        fixture.then(info.getTestMethod().orElseThrow(), null, r -> {
            assertNotNull(JenkinsLocationConfiguration.get().getUrl());
            JenkinsLocationConfiguration.get().setUrl("https://example.com/");
        });
        fixture.then(info.getTestMethod().orElseThrow(), null, r -> {
            assertEquals(
                    "https://example.com/", JenkinsLocationConfiguration.get().getUrl());
        });
    }

    @Test
    void test500Errors() throws IOException {
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(500);
        assertThrows(RealJenkinsFixture.JenkinsStartupException.class, () -> RealJenkinsExtension.checkResult(conn));
    }

    @Test
    void test503Errors() throws IOException {
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(503);
        when(conn.getErrorStream())
                .thenReturn(new ByteArrayInputStream("Jenkins Custom Error".getBytes(StandardCharsets.UTF_8)));

        String s = RealJenkinsExtension.checkResult(conn);

        assertThat(s, is("Jenkins Custom Error"));
    }

    @Test
    void test200Ok() throws IOException {
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(200);
        when(conn.getInputStream())
                .thenReturn(new ByteArrayInputStream("blah blah blah".getBytes(StandardCharsets.UTF_8)));

        String s = RealJenkinsExtension.checkResult(conn);

        verify(conn, times(1)).getInputStream();
        assertThat(s, nullValue());
    }

    /**
     * plugins/failure.hpi
     * Plugin that has this:
     *
     * @Initializer(after=JOB_LOADED) public static void init() throws IOException {
     * throw new IOException("oops");
     * }
     */
    @Test
    void whenUsingFailurePlugin(TestInfo info) throws Throwable {
        RealJenkinsFixture.JenkinsStartupException jse = assertThrows(
                RealJenkinsFixture.JenkinsStartupException.class, () -> fixture.addPlugins("plugins/failure.hpi")
                        .startJenkins(info.getTestMethod().orElseThrow(), null));
        assertThat(jse.getMessage(), containsString("Error</h1><pre>java.io.IOException: oops"));
    }

    @Test
    void whenUsingWrongJavaHome(TestInfo info) throws Throwable {
        IOException ex = assertThrows(IOException.class, () -> fixture.withJavaHome("/noexists")
                .startJenkins(info.getTestMethod().orElseThrow(), null));
        assertThat(
                ex.getMessage(),
                containsString(File.separator + "noexists" + File.separator + "bin" + File.separator + "java"));
    }

    @Test
    void smokesJavaHome(TestInfo info) throws Throwable {
        String altJavaHome = System.getProperty("java.home");
        fixture.addPlugins("plugins/structs.hpi");
        fixture.extraEnv("SOME_ENV_VAR", "value")
                .extraEnv("NOT_SET", null)
                .withJavaHome(altJavaHome)
                .withLogger(Jenkins.class, Level.FINEST)
                .then(info.getTestMethod().orElseThrow(), null, RealJenkinsFixtureTest::_smokes);
    }

    @Issue("https://github.com/jenkinsci/jenkins-test-harness/issues/359")
    @Test
    void assumptions(TestInfo info) throws Throwable {
        assertThat(
                assertThrows(
                                TestAbortedException.class,
                                () -> fixture.then(
                                        info.getTestMethod().orElseThrow(),
                                        null,
                                        RealJenkinsFixtureTest::_assumptions1))
                        .getMessage(),
                is("Assumption failed: assumption is not true"));
        assertThat(
                assertThrows(
                                TestAbortedException.class,
                                () -> fixture.then(
                                        info.getTestMethod().orElseThrow(),
                                        null,
                                        RealJenkinsFixtureTest::_assumptions2))
                        .getMessage(),
                is("Assumption failed: oops"));
    }

    private static void _assumptions1(JenkinsRule r) {
        assumeTrue(2 + 2 == 5);
    }

    private static void _assumptions2(JenkinsRule r) {
        assumeTrue(2 + 2 == 5, "oops");
    }

    @Test
    void timeoutDuringStep(TestInfo info) throws Throwable {
        fixture.withTimeout(10);
        assertThat(
                Functions.printThrowable(assertThrows(
                        RealJenkinsFixture.StepException.class,
                        () -> fixture.then(info.getTestMethod().orElseThrow(), null, RealJenkinsFixtureTest::hangs))),
                containsString(
                        "\tat " + RealJenkinsFixtureTest.class.getName() + ".hangs(RealJenkinsFixtureTest.java:"));
    }

    private static void hangs(JenkinsRule r) throws Throwable {
        System.err.println("Hanging step…");
        Thread.sleep(Long.MAX_VALUE);
    }

    @Test
    void noDetachedPlugins(TestInfo info) throws Throwable {
        // we should be the only plugin in Jenkins.
        fixture.then(info.getTestMethod().orElseThrow(), null, RealJenkinsFixtureTest::_noDetachedPlugins);
    }

    private static void _noDetachedPlugins(JenkinsRule r) throws Throwable {
        // only RealJenkinsRuleInit should be present
        List<PluginWrapper> plugins = r.jenkins.getPluginManager().getPlugins();
        assertThat(plugins, hasSize(1));
        assertThat(plugins.get(0).getShortName(), is("RealJenkinsFixtureInit"));
    }

    @Test
    void safeExit(TestInfo info) throws Throwable {
        fixture.then(info.getTestMethod().orElseThrow(), null, r -> {
            var p = r.createFreeStyleProject();
            p.getBuildersList().add(TestBuilder.of((build, launcher, listener) -> Thread.sleep(Long.MAX_VALUE)));
            p.scheduleBuild2(0).waitForStart();
        });
    }

    @Test
    void xStreamSerializable(TestInfo info) throws Throwable {
        fixture.startJenkins(info.getTestMethod().orElseThrow(), null);
        // Neither ParametersDefinitionProperty nor ParametersAction could be passed directly.
        // (In this case, ParameterDefinition and ParameterValue could have been used raw.
        // But even List<ParameterValue> cannot be typed here, only e.g. ArrayList<ParameterValue>.)
        var prop = XStreamSerializable.of(new ParametersDefinitionProperty(new StringParameterDefinition("X", "dflt")));
        // Static method handle idiom:
        assertThat(
                fixture.runRemotely(RealJenkinsFixtureTest::_xStreamSerializable, prop)
                        .object()
                        .getAllParameters(),
                hasSize(1));
        // Lambda idiom:
        assertThat(
                fixture.runRemotely(r -> {
                            var p = r.createFreeStyleProject();
                            p.addProperty(prop.object());
                            var b = r.buildAndAssertSuccess(p);
                            return XStreamSerializable.of(b.getAction(ParametersAction.class));
                        })
                        .object()
                        .getAllParameters(),
                hasSize(1));
    }

    private static XStreamSerializable<ParametersAction> _xStreamSerializable(
            JenkinsRule r, XStreamSerializable<? extends JobProperty<? super FreeStyleProject>> prop) throws Throwable {
        var p = r.createFreeStyleProject();
        p.addProperty(prop.object());
        var b = r.buildAndAssertSuccess(p);
        return XStreamSerializable.of(b.getAction(ParametersAction.class));
    }

    @Disabled(
            "inner class inside lambda breaks with an opaque NotSerializableException: RealJenkinsExtensionTest; use TestBuilder.of instead")
    @Test
    void lambduh(TestInfo info) throws Throwable {
        fixture.then(info.getTestMethod().orElseThrow(), null, r -> {
            r.createFreeStyleProject().getBuildersList().add(new TestBuilder() {
                @Override
                public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                        throws InterruptedException, IOException {
                    return true;
                }
            });
        });
    }
}
