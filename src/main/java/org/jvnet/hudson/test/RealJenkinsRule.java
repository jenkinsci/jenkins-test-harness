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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ExtensionList;
import io.jenkins.test.fips.FIPSTestBundleProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.logging.Level;
import javax.net.ssl.SSLContext;
import jenkins.test.https.KeyStoreManager;
import org.htmlunit.WebClient;
import org.junit.rules.ErrorCollector;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.fixtures.JenkinsSessionFixture;
import org.jvnet.hudson.test.fixtures.RealJenkinsFixture;
import org.jvnet.hudson.test.fixtures.RealJenkinsFixtureInit;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * This is the JUnit 4 implementation of {@link RealJenkinsFixture}.
 * Usage: <pre>{@code
 * @Rule
 * public final RealJenkinsRule rr = new RealJenkinsRule();
 * }</pre>
 *
 * <p>Known limitations:
 * <ul>
 * <li>Execution is a bit slower due to the overhead of launching a new JVM; and class loading overhead cannot be shared between test cases. More memory is needed.
 * <li>Remote calls must be serializable. Use methods like {@link #runRemotely(RealJenkinsRule.StepWithReturnAndOneArg, Serializable)} and/or {@link XStreamSerializable} as needed.
 * <li>{@code static} state cannot be shared between the top-level test code and test bodies (though the compiler will not catch this mistake).
 * <li>When using a snapshot dep on Jenkins core, you must build {@code jenkins.war} to test core changes (there is no “compile-on-save” support for this).
 * <li>{@link TestExtension} is not available (but try {@link #addSyntheticPlugin}).
 * <li>{@link LoggerRule} is not available, however additional loggers can be configured via {@link #withLogger(Class, Level)}}.
 * <li>{@link BuildWatcher} is not available, but you can use {@link TailLog} instead.
 * </ul>
 *
 * @see JenkinsRule
 * @see JenkinsSessionFixture
 * @see org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension
 * @see org.jvnet.hudson.test.RealJenkinsRule
 * @see RealJenkinsFixtureInit
 */
public final class RealJenkinsRule implements TestRule {

    private final RealJenkinsFixture fixture;
    private Description description;

    public RealJenkinsRule() {
        fixture = new RealJenkinsFixture();
    }

    /**
     * Links this rule to another, with {@link #getHome} to be initialized by whichever copy starts first.
     * Also copies configuration related to the setup of that directory:
     * {@link #includeTestClasspathPlugins(boolean)}, {@link #addPlugins}, {@link #addSyntheticPlugin}, and {@link #omitPlugins}.
     * Other configuration such as {@link #javaOptions(String...)} may be applied to both, but that is your choice.
     */
    public RealJenkinsRule(RealJenkinsRule source) {
        fixture = new RealJenkinsFixture(source.fixture);
        description = source.description;
    }

    /**
     * Add some plugins to the test classpath.
     *
     * @param plugins Filenames of the plugins to install. These are expected to be absolute test classpath resources,
     *     such as {@code plugins/workflow-job.hpi} for example.
     *     <p>For small fake plugins built for this purpose and exercising some bit of code, use {@link #addSyntheticPlugin}.
     *     If you wish to test with larger archives of real plugins, this is possible for example by
     *     binding {@code dependency:copy} to the {@code process-test-resources} phase.
     *     <p>In most cases you do not need this method. Simply add whatever plugins you are
     *     interested in testing against to your POM in {@code test} scope. These, and their
     *     transitive dependencies, will be loaded in all {@link RealJenkinsRule} tests. This method
     *     is useful if only a particular test may load the tested plugin, or if the tested plugin
     *     is not available in a repository for use as a test dependency.
     */
    public RealJenkinsRule addPlugins(String... plugins) {
        fixture.addPlugins(plugins);
        return this;
    }

    /**
     * Adds a test-only plugin to the controller based on sources defined in this module.
     * Useful when you wish to define some types, register some {@link Extension}s, etc.
     * and there is no existing plugin that does quite what you want
     * (that you are comfortable adding to the test classpath and maintaining the version of).
     * <p>If you also have some test suites based on {@link JenkinsRule},
     * you may not want to use {@link Extension} since (unlike {@link TestExtension})
     * it would be loaded in all such tests.
     * Instead create a {@code package-info.java} specifying an {@code @OptionalPackage}
     * whose {@code requirePlugins} lists the same {@link SyntheticPlugin#shortName(String)}.
     * (You will need to {@code .header("Plugin-Dependencies", "variant:0")} to use this API.)
     * Then use {@code @OptionalExtension} on all your test extensions.
     * These will then be loaded only in {@link RealJenkinsRule}-based tests requesting this plugin.
     * @param plugin the configured {@link SyntheticPlugin}
     */
    public RealJenkinsRule addSyntheticPlugin(SyntheticPlugin plugin) {
        fixture.addSyntheticPlugin(plugin.delegate);
        return this;
    }

    /**
     * Creates a test-only plugin based on sources defined in this module, but does not install it.
     * <p>See {@link #addSyntheticPlugin} for more details. Prefer that method if you simply want the
     * plugin to be installed automatically.
     * @see #addSyntheticPlugin
     * @param plugin the configured {@link SyntheticPlugin}
     * @return the JPI file for the plugin
     */
    public File createSyntheticPlugin(SyntheticPlugin plugin) throws IOException, URISyntaxException {
        return fixture.createSyntheticPlugin(plugin.delegate);
    }

    /**
     * Omit some plugins in the test classpath.
     * @param plugins one or more code names, like {@code token-macro}
     */
    public RealJenkinsRule omitPlugins(String... plugins) {
        fixture.omitPlugins(plugins);
        return this;
    }

    /**
     * Add some JVM startup options.
     * @param options one or more options, like {@code -Dorg.jenkinsci.Something.FLAG=true}
     */
    public RealJenkinsRule javaOptions(String... options) {
        fixture.javaOptions(options);
        return this;
    }

    /**
     * Add some Jenkins (including Winstone) startup options.
     * You probably meant to use {@link #javaOptions(String...)}.
     * @param options one or more options, like {@code --compression=none --requestHeaderSize=100000}
     */
    public RealJenkinsRule jenkinsOptions(String... options) {
        fixture.jenkinsOptions(options);
        return this;
    }

    /**
     * Set an extra environment variable.
     * @param value null to cancel a previously set variable
     */
    public RealJenkinsRule extraEnv(String key, String value) {
        fixture.extraEnv(key, value);
        return this;
    }

    /**
     * Adjusts the test timeout.
     * The timer starts when {@link #startJenkins} completes and {@link #runRemotely} is ready.
     * The default is currently set to 600 (10m).
     * @param timeout number of seconds before exiting, or zero to disable
     */
    public RealJenkinsRule withTimeout(int timeout) {
        fixture.withTimeout(timeout);
        return this;
    }

    /**
     * Sets a custom host name for the Jenkins root URL.
     * <p>By default, this is just {@code localhost}.
     * But you may wish to set it to something else that resolves to localhost,
     * such as {@code some-id.localtest.me}.
     * This is particularly useful when running multiple copies of Jenkins (and/or other services) in one test case,
     * since browser cookies are sensitive to host but not port and so otherwise {@link HttpServletRequest#getSession}
     * might accidentally be shared across otherwise distinct services.
     * <p>Calling this method does <em>not</em> change the fact that Jenkins will be configured to listen only on localhost for security reasons
     * (so others in the same network cannot access your system under test, especially if it lacks authentication).
     * <p>
     * When using HTTPS, use {@link #https(String,KeyStoreManager, X509Certificate)} instead.
     */
    public RealJenkinsRule withHost(String host) {
        fixture.withHost(host);
        return this;
    }

    /**
     * Sets a custom prefix for the Jenkins root URL.
     * <p>
     * By default, the prefix defaults to {@code /jenkins}.
     * <p>
     * If not empty, must start with '/' and not end with '/'.
     */
    public RealJenkinsRule withPrefix(@NonNull String prefix) {
        fixture.withPrefix(prefix);
        return this;
    }

    /**
     * Sets a custom WAR file to be used by the rule instead of the one in the path or {@code war/target/jenkins.war} in case of core.
     */
    public RealJenkinsRule withWar(File war) {
        fixture.withWar(war);
        return this;
    }

    /**
     * Allows to specify a java home, defaults to JAVA_HOME if not used
     */
    public RealJenkinsRule withJavaHome(String javaHome) {
        fixture.withJavaHome(javaHome);
        return this;
    }

    public RealJenkinsRule withLogger(Class<?> clazz, Level level) {
        fixture.withLogger(clazz.getName(), level);
        return this;
    }

    public RealJenkinsRule withPackageLogger(Class<?> clazz, Level level) {
        fixture.withLogger(clazz.getPackageName(), level);
        return this;
    }

    public RealJenkinsRule withLogger(String logger, Level level) {
        fixture.withLogger(logger, level);
        return this;
    }

    /**
     * Sets a name for this instance, which will be prefixed to log messages to simplify debugging.
     */
    public RealJenkinsRule withName(String name) {
        fixture.withName(name);
        return this;
    }

    public String getName() {
        return fixture.getName();
    }

    /**
     * Applies ANSI coloration to log lines produced by this instance, complementing {@link #withName}.
     * Ignored when on CI.
     */
    public RealJenkinsRule withColor(PrefixedOutputStream.AnsiColor color) {
        fixture.withColor(color);
        return this;
    }

    /**
     * Provides a custom fixed port instead of a random one.
     * @param port a custom port to use instead of a random one.
     */
    public RealJenkinsRule withPort(int port) {
        fixture.withPort(port);
        return this;
    }

    /**
     * Provides a custom interface to listen to.
     * <p><em>Important:</em> for security reasons this should be overridden only in special scenarios,
     * such as testing inside a Docker container.
     * Otherwise a developer running tests could inadvertently expose a Jenkins service without password protection,
     * allowing remote code execution.
     * @param httpListenAddress network interface such as <pre>0.0.0.0</pre>. Defaults to <pre>127.0.0.1</pre>.
     */
    public RealJenkinsRule withHttpListenAddress(String httpListenAddress) {
        fixture.withHttpListenAddress(httpListenAddress);
        return this;
    }

    /**
     * Allows usage of a static debug port instead of a random one.
     * <p>
     * This allows to use predefined debug configurations in the IDE.
     * <p>
     * Typical usage is in a base test class where multiple named controller instances are defined with fixed ports
     *
     * <pre>
     * public RealJenkinsRule cc1 = new RealJenkinsRule().withName("cc1").withDebugPort(4001).withDebugServer(false);
     *
     * public RealJenkinsRule cc2 = new RealJenkinsRule().withName("cc2").withDebugPort(4002).withDebugServer(false);
     * </pre>
     *
     * Then have debug configurations in the IDE set for ports
     * <ul>
     * <li>5005 (test VM) - debugger mode "attach to remote vm"</li>
     * <li>4001 (cc1) - debugger mode "listen to remote vm"</li>
     * <li>4002 (cc2) - debugger mode "listen to remote vm"</li>
     * </ul>
     * <p>
     * This allows for debugger to reconnect in scenarios where restarts of controllers are involved.
     *
     * @param debugPort the TCP port to use for debugging this Jenkins instance. Between 0 (random) and 65536 (excluded).
     */
    public RealJenkinsRule withDebugPort(int debugPort) {
        fixture.withDebugPort(debugPort);
        return this;
    }
    /**
     * Allows to use debug in server mode or client mode. Client mode is friendlier to controller restarts.
     *
     * @see #withDebugPort(int)
     *
     * @param debugServer true to use server=y, false to use server=n
     */
    public RealJenkinsRule withDebugServer(boolean debugServer) {
        fixture.withDebugServer(debugServer);
        return this;
    }

    /**
     * Whether to suspend the controller VM on startup until debugger is connected. Defaults to false.
     * @param debugSuspend true to suspend the controller VM on startup until debugger is connected.
     */
    public RealJenkinsRule withDebugSuspend(boolean debugSuspend) {
        fixture.withDebugSuspend(debugSuspend);
        return this;
    }

    /**
     * The intended use case for this is to use the plugins bundled into the war {@link RealJenkinsRule#withWar(File)}
     * instead of the plugins in the pom. A typical scenario for this feature is a test which does not live inside a
     * plugin's src/test/java
     * @param includeTestClasspathPlugins false if plugins from pom should not be used (default true)
     */
    public RealJenkinsRule includeTestClasspathPlugins(boolean includeTestClasspathPlugins) {
        fixture.includeTestClasspathPlugins(includeTestClasspathPlugins);
        return this;
    }

    /**
     * Allows {@code JENKINS_HOME} initialization to be delayed until {@link #startJenkins} is called for the first time.
     * <p>
     * This allows methods such as {@link #addPlugins} to be called dynamically inside of test methods, which enables
     * related tests that need to configure {@link RealJenkinsRule} in different ways to be defined in the same class
     * using only a single instance of {@link RealJenkinsRule}.
     */
    public RealJenkinsRule prepareHomeLazily(boolean prepareHomeLazily) {
        fixture.prepareHomeLazily(prepareHomeLazily);
        return this;
    }

    /**
     * Use {@link #withFIPSEnabled(FIPSTestBundleProvider)}  with default value of {@link FIPSTestBundleProvider#get()}
     */
    public RealJenkinsRule withFIPSEnabled() {
        fixture.withFIPSEnabled(FIPSTestBundleProvider.get());
        return this;
    }

    /**
     * +
     * @param fipsTestBundleProvider the {@link FIPSTestBundleProvider} to use for testing
     */
    public RealJenkinsRule withFIPSEnabled(FIPSTestBundleProvider fipsTestBundleProvider) {
        fixture.withFIPSEnabled(fipsTestBundleProvider);
        return this;
    }

    /**
     *
     * @param files add some {@link File} to bootclasspath
     */
    public RealJenkinsRule withBootClasspath(File... files) {
        fixture.withBootClasspath(files);
        return this;
    }

    public static List<String> getJacocoAgentOptions() {
        return RealJenkinsFixture.getJacocoAgentOptions();
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        this.description = description;

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    fixture.setUp(
                            description.getClassName(),
                            description.getMethodName(),
                            description.getAnnotations().toArray(new Annotation[0]));
                    base.evaluate();
                } finally {
                    fixture.tearDown();
                }
            }
        };
    }

    /**
     * Deletes {@code JENKINS_HOME}.
     *
     * This method does not need to be invoked when using {@code @Rule} or {@code @ClassRule} to run {@code RealJenkinsRule}.
     */
    public void deprovision() throws Exception {
        fixture.deprovision();
    }

    /**
     * Creates a temporary directory.
     * Unlike {@link Files#createTempDirectory(String, FileAttribute...)}
     * this will be cleaned up after the test exits (like {@link TemporaryFolder}),
     * and will honor {@code java.io.tmpdir} set by Surefire
     * after {@code StaticProperty.JAVA_IO_TMPDIR} has been initialized.
     */
    public Path createTempDirectory(String prefix) throws IOException {
        return fixture.createTempDirectory(prefix);
    }

    /**
     * Returns true if the Jenkins process is alive.
     */
    public boolean isAlive() {
        return fixture.isAlive();
    }

    /**
     * Returns the Jenkins process handle .
     * @return the process handle
     */
    public Process getProcess() {
        return fixture.getProcess();
    }

    public String[] getTruststoreJavaOptions() {
        return fixture.getTruststoreJavaOptions();
    }

    /**
     * One step to run.
     * <p>Since this thunk will be sent to a different JVM, it must be serializable.
     * The test class will certainly not be serializable, so you cannot use an anonymous inner class.
     * One idiom is a static method reference:
     * <pre>
     * &#64;Test public void stuff() throws Throwable {
     *     rr.then(YourTest::_stuff);
     * }
     * private static void _stuff(JenkinsRule r) throws Throwable {
     *     // as needed
     * }
     * </pre>
     * If you need to pass and/or return values, you can still use a static method reference:
     * try {@link #runRemotely(Step2)} or {@link #runRemotely(StepWithReturnAndOneArg, Serializable)} etc.
     * (using {@link XStreamSerializable} as needed).
     * <p>
     * Alternately, you could use a lambda:
     * <pre>
     * &#64;Test public void stuff() throws Throwable {
     *     rr.then(r -> {
     *         // as needed
     *     });
     * }
     * </pre>
     * In this case you must take care not to capture non-serializable objects from scope;
     * in particular, the body must not use (named or anonymous) inner classes.
     */
    @FunctionalInterface
    public interface Step extends RealJenkinsFixture.Step {
        void run(JenkinsRule r) throws Throwable;
    }

    @FunctionalInterface
    public interface Step2<T extends Serializable> extends RealJenkinsFixture.Step2<T> {
        T run(JenkinsRule r) throws Throwable;
    }

    /**
     * Run one Jenkins session, send one or more test thunks, and shut down.
     */
    public void then(Step... steps) throws Throwable {
        if (description == null) {
            throw new IllegalStateException("RealJenkinsRule must be registered via @Rule");
        }
        fixture.then(steps);
    }

    /**
     * Run one Jenkins session, send a test thunk, and shut down.
     */
    public <T extends Serializable> T then(Step2<T> s) throws Throwable {
        startJenkins();
        try {
            return runRemotely(s);
        } finally {
            stopJenkins();
        }
    }

    /**
     * Similar to {@link JenkinsRule#getURL}. Requires Jenkins to be started before using {@link #startJenkins()}.
     * <p>
     * Always ends with a '/'.
     */
    public URL getUrl() throws MalformedURLException {
        return fixture.getUrl();
    }

    /**
     * Sets up HTTPS for the current instance, and disables plain HTTP.
     * This generates a self-signed certificate for <em>localhost</em>. The corresponding root CA that needs to be trusted by HTTP client can be obtained using {@link #getRootCA()}.
     *
     * @return the current instance
     * @see #createWebClient()
     */
    public RealJenkinsRule https() {
        fixture.https();
        return this;
    }

    /**
     * Sets up HTTPS for the current instance, and disables plain HTTP.
     * <p>
     * You don't need to call {@link #withHost(String)} when calling this method.
     *
     * @param host the host name to use in the certificate
     * @param keyStoreManager a key store manager containing the key and certificate to use for HTTPS. It needs to be valid for the given host
     * @param rootCA the certificate that needs to be trusted by callers.
     * @return the current instance
     * @see #createWebClient()
     * @see #withHost(String)
     */
    public RealJenkinsRule https(
            @NonNull String host, @NonNull KeyStoreManager keyStoreManager, @NonNull X509Certificate rootCA) {
        fixture.https(host, keyStoreManager, rootCA);
        return this;
    }

    /**
     * @return the current autogenerated root CA or null if {@link #https()} has not been called.
     */
    @Nullable
    public X509Certificate getRootCA() {
        return fixture.getRootCA();
    }

    /**
     * Returns the autogenerated self-signed root CA in PEM format, or null if {@link #https()} has not been called.
     * Typically used to configure {@link InboundAgentRule.Options.Builder#cert}.
     * @return the root CA in PEM format, or null if unavailable
     */
    @Nullable
    public String getRootCAPem() {
        return fixture.getRootCAPem();
    }

    /**
     * Builds a {@link SSLContext} trusting the current instance.
     */
    @NonNull
    public SSLContext buildSSLContext() throws NoSuchAlgorithmException {
        return fixture.buildSSLContext();
    }

    /**
     * Obtains the Jenkins home directory.
     * Normally it will suffice to use {@link LocalData} to populate files.
     */
    public File getHome() {
        return fixture.getHome();
    }

    /**
     * Switch the Jenkins home directory.
     * Will affect subsequent startups of this rule,
     * but not other copies linked via {@link RealJenkinsRule#RealJenkinsRule(RealJenkinsRule)}.
     * Normally unnecessary but could be used to simulate running on the wrong home.
     */
    public void setHome(File newHome) {
        fixture.setHome(newHome);
    }

    /**
     * Create a client configured to trust any self-signed certificate used by this instance.
     */
    public WebClient createWebClient() {
        return fixture.createWebClient();
    }

    public void startJenkins() throws Throwable {
        if (description == null) {
            throw new IllegalStateException("RealJenkinsRule must be registered via @Rule");
        }
        fixture.startJenkins();
    }

    @CheckForNull
    public static String checkResult(HttpURLConnection conn) throws IOException {
        return RealJenkinsFixture.checkResult(conn);
    }

    /**
     * Stops Jenkins and releases any system resources associated
     * with it. If Jenkins is already stopped then invoking this
     * method has no effect.
     */
    public void stopJenkins() throws Throwable {
        fixture.stopJenkins();
    }

    /**
     * Stops Jenkins abruptly, without giving it a chance to shut down cleanly.
     * If Jenkins is already stopped then invoking this method has no effect.
     */
    public void stopJenkinsForcibly() {
        fixture.stopJenkinsForcibly();
    }

    /**
     * Runs one or more steps on the remote system.
     * (Compared to multiple calls, passing a series of steps is slightly more efficient
     * as only one network call is made.)
     */
    public void runRemotely(Step... steps) throws Throwable {
        fixture.runRemotely(steps);
    }

    /**
     * Run a step on the remote system.
     * Alias for {@link #runRemotely(RealJenkinsRule.Step...)} (with one step)
     * that is easier to resolve for lambdas.
     */
    public void run(Step step) throws Throwable {
        fixture.runRemotely(step);
    }

    /**
     * Run a step on the remote system, but do not immediately fail, just record any error.
     * Same as {@link ErrorCollector#checkSucceeds} but more concise to call.
     */
    public void run(ErrorCollector errors, Step step) {
        fixture.run(errors, step);
    }

    public <T extends Serializable> T runRemotely(Step2<T> s) throws Throwable {
        return fixture.runRemotely(s);
    }

    /**
     * Run a step with a return value on the remote system.
     * Alias for {@link #runRemotely(RealJenkinsRule.Step2)}
     * that is easier to resolve for lambdas.
     */
    public <T extends Serializable> T call(Step2<T> s) throws Throwable {
        return fixture.runRemotely(s);
    }

    @FunctionalInterface
    public interface StepWithOneArg<A1 extends Serializable> extends RealJenkinsFixture.StepWithOneArg<A1> {
        void run(JenkinsRule r, A1 arg1) throws Throwable;
    }

    public <A1 extends Serializable> void runRemotely(StepWithOneArg<A1> s, A1 arg1) throws Throwable {
        runRemotely(new StepWithOneArgWrapper<>(s, arg1));
    }

    private static final class StepWithOneArgWrapper<A1 extends Serializable> implements Step {
        private final StepWithOneArg<A1> delegate;
        private final A1 arg1;

        StepWithOneArgWrapper(StepWithOneArg<A1> delegate, A1 arg1) {
            this.delegate = delegate;
            this.arg1 = arg1;
        }

        @Override
        public void run(JenkinsRule r) throws Throwable {
            delegate.run(r, arg1);
        }
    }

    @FunctionalInterface
    public interface StepWithTwoArgs<A1 extends Serializable, A2 extends Serializable>
            extends RealJenkinsFixture.StepWithTwoArgs<A1, A2> {
        void run(JenkinsRule r, A1 arg1, A2 arg2) throws Throwable;
    }

    public <A1 extends Serializable, A2 extends Serializable> void runRemotely(
            StepWithTwoArgs<A1, A2> s, A1 arg1, A2 arg2) throws Throwable {
        runRemotely(new StepWithTwoArgsWrapper<>(s, arg1, arg2));
    }

    private static final class StepWithTwoArgsWrapper<A1 extends Serializable, A2 extends Serializable>
            implements Step {
        private final StepWithTwoArgs<A1, A2> delegate;
        private final A1 arg1;
        private final A2 arg2;

        StepWithTwoArgsWrapper(StepWithTwoArgs<A1, A2> delegate, A1 arg1, A2 arg2) {
            this.delegate = delegate;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        public void run(JenkinsRule r) throws Throwable {
            delegate.run(r, arg1, arg2);
        }
    }

    @FunctionalInterface
    public interface StepWithThreeArgs<A1 extends Serializable, A2 extends Serializable, A3 extends Serializable>
            extends RealJenkinsFixture.StepWithThreeArgs<A1, A2, A3> {
        void run(JenkinsRule r, A1 arg1, A2 arg2, A3 arg3) throws Throwable;
    }

    public <A1 extends Serializable, A2 extends Serializable, A3 extends Serializable> void runRemotely(
            StepWithThreeArgs<A1, A2, A3> s, A1 arg1, A2 arg2, A3 arg3) throws Throwable {
        runRemotely(new StepWithThreeArgsWrapper<>(s, arg1, arg2, arg3));
    }

    private static final class StepWithThreeArgsWrapper<
                    A1 extends Serializable, A2 extends Serializable, A3 extends Serializable>
            implements Step {
        private final StepWithThreeArgs<A1, A2, A3> delegate;
        private final A1 arg1;
        private final A2 arg2;
        private final A3 arg3;

        StepWithThreeArgsWrapper(StepWithThreeArgs<A1, A2, A3> delegate, A1 arg1, A2 arg2, A3 arg3) {
            this.delegate = delegate;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
        }

        @Override
        public void run(JenkinsRule r) throws Throwable {
            delegate.run(r, arg1, arg2, arg3);
        }
    }

    @FunctionalInterface
    public interface StepWithFourArgs<
                    A1 extends Serializable, A2 extends Serializable, A3 extends Serializable, A4 extends Serializable>
            extends RealJenkinsFixture.StepWithFourArgs<A1, A2, A3, A4> {
        void run(JenkinsRule r, A1 arg1, A2 arg2, A3 arg3, A4 arg4) throws Throwable;
    }

    public <A1 extends Serializable, A2 extends Serializable, A3 extends Serializable, A4 extends Serializable>
            void runRemotely(StepWithFourArgs<A1, A2, A3, A4> s, A1 arg1, A2 arg2, A3 arg3, A4 arg4) throws Throwable {
        runRemotely(new StepWithFourArgsWrapper<>(s, arg1, arg2, arg3, arg4));
    }

    private static final class StepWithFourArgsWrapper<
                    A1 extends Serializable, A2 extends Serializable, A3 extends Serializable, A4 extends Serializable>
            implements Step {
        private final StepWithFourArgs<A1, A2, A3, A4> delegate;
        private final A1 arg1;
        private final A2 arg2;
        private final A3 arg3;
        private final A4 arg4;

        StepWithFourArgsWrapper(StepWithFourArgs<A1, A2, A3, A4> delegate, A1 arg1, A2 arg2, A3 arg3, A4 arg4) {
            this.delegate = delegate;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
            this.arg4 = arg4;
        }

        @Override
        public void run(JenkinsRule r) throws Throwable {
            delegate.run(r, arg1, arg2, arg3, arg4);
        }
    }

    @FunctionalInterface
    public interface StepWithReturnAndOneArg<R extends Serializable, A1 extends Serializable>
            extends RealJenkinsFixture.StepWithReturnAndOneArg<R, A1> {
        R run(JenkinsRule r, A1 arg1) throws Throwable;
    }

    public <R extends Serializable, A1 extends Serializable> R runRemotely(StepWithReturnAndOneArg<R, A1> s, A1 arg1)
            throws Throwable {
        return runRemotely(new StepWithReturnAndOneArgWrapper<>(s, arg1));
    }

    private static final class StepWithReturnAndOneArgWrapper<R extends Serializable, A1 extends Serializable>
            implements Step2<R> {
        private final StepWithReturnAndOneArg<R, A1> delegate;
        private final A1 arg1;

        StepWithReturnAndOneArgWrapper(StepWithReturnAndOneArg<R, A1> delegate, A1 arg1) {
            this.delegate = delegate;
            this.arg1 = arg1;
        }

        @Override
        public R run(JenkinsRule r) throws Throwable {
            return delegate.run(r, arg1);
        }
    }

    @FunctionalInterface
    public interface StepWithReturnAndTwoArgs<R extends Serializable, A1 extends Serializable, A2 extends Serializable>
            extends RealJenkinsFixture.StepWithReturnAndTwoArgs<R, A1, A2> {
        R run(JenkinsRule r, A1 arg1, A2 arg2) throws Throwable;
    }

    public <R extends Serializable, A1 extends Serializable, A2 extends Serializable> R runRemotely(
            StepWithReturnAndTwoArgs<R, A1, A2> s, A1 arg1, A2 arg2) throws Throwable {
        return runRemotely(new StepWithReturnAndTwoArgsWrapper<>(s, arg1, arg2));
    }

    private static final class StepWithReturnAndTwoArgsWrapper<
                    R extends Serializable, A1 extends Serializable, A2 extends Serializable>
            implements Step2<R> {
        private final StepWithReturnAndTwoArgs<R, A1, A2> delegate;
        private final A1 arg1;
        private final A2 arg2;

        StepWithReturnAndTwoArgsWrapper(StepWithReturnAndTwoArgs<R, A1, A2> delegate, A1 arg1, A2 arg2) {
            this.delegate = delegate;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        public R run(JenkinsRule r) throws Throwable {
            return delegate.run(r, arg1, arg2);
        }
    }

    @FunctionalInterface
    public interface StepWithReturnAndThreeArgs<
                    R extends Serializable, A1 extends Serializable, A2 extends Serializable, A3 extends Serializable>
            extends RealJenkinsFixture.StepWithReturnAndThreeArgs<R, A1, A2, A3> {
        R run(JenkinsRule r, A1 arg1, A2 arg2, A3 arg3) throws Throwable;
    }

    public <R extends Serializable, A1 extends Serializable, A2 extends Serializable, A3 extends Serializable>
            R runRemotely(StepWithReturnAndThreeArgs<R, A1, A2, A3> s, A1 arg1, A2 arg2, A3 arg3) throws Throwable {
        return runRemotely(new StepWithReturnAndThreeArgsWrapper<>(s, arg1, arg2, arg3));
    }

    private static final class StepWithReturnAndThreeArgsWrapper<
                    R extends Serializable, A1 extends Serializable, A2 extends Serializable, A3 extends Serializable>
            implements Step2<R> {
        private final StepWithReturnAndThreeArgs<R, A1, A2, A3> delegate;
        private final A1 arg1;
        private final A2 arg2;
        private final A3 arg3;

        StepWithReturnAndThreeArgsWrapper(
                StepWithReturnAndThreeArgs<R, A1, A2, A3> delegate, A1 arg1, A2 arg2, A3 arg3) {
            this.delegate = delegate;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
        }

        @Override
        public R run(JenkinsRule r) throws Throwable {
            return delegate.run(r, arg1, arg2, arg3);
        }
    }

    @FunctionalInterface
    public interface StepWithReturnAndFourArgs<
                    R extends Serializable,
                    A1 extends Serializable,
                    A2 extends Serializable,
                    A3 extends Serializable,
                    A4 extends Serializable>
            extends RealJenkinsFixture.StepWithReturnAndFourArgs<R, A1, A2, A3, A4> {
        R run(JenkinsRule r, A1 arg1, A2 arg2, A3 arg3, A4 arg4) throws Throwable;
    }

    public <
                    R extends Serializable,
                    A1 extends Serializable,
                    A2 extends Serializable,
                    A3 extends Serializable,
                    A4 extends Serializable>
            R runRemotely(StepWithReturnAndFourArgs<R, A1, A2, A3, A4> s, A1 arg1, A2 arg2, A3 arg3, A4 arg4)
                    throws Throwable {
        return runRemotely(new StepWithReturnAndFourArgsWrapper<>(s, arg1, arg2, arg3, arg4));
    }

    private static final class StepWithReturnAndFourArgsWrapper<
                    R extends Serializable,
                    A1 extends Serializable,
                    A2 extends Serializable,
                    A3 extends Serializable,
                    A4 extends Serializable>
            implements Step2<R> {
        private final StepWithReturnAndFourArgs<R, A1, A2, A3, A4> delegate;
        private final A1 arg1;
        private final A2 arg2;
        private final A3 arg3;
        private final A4 arg4;

        StepWithReturnAndFourArgsWrapper(
                StepWithReturnAndFourArgs<R, A1, A2, A3, A4> delegate, A1 arg1, A2 arg2, A3 arg3, A4 arg4) {
            this.delegate = delegate;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
            this.arg4 = arg4;
        }

        @Override
        public R run(JenkinsRule r) throws Throwable {
            return delegate.run(r, arg1, arg2, arg3, arg4);
        }
    }

    /**
     * Alternative to {@link #addPlugins} or {@link TestExtension} that lets you build a test-only plugin on the fly.
     * ({@link ExtensionList#add(Object)} can also be used for certain cases, but not if you need to define new types.)
     */
    public static final class SyntheticPlugin {
        private final RealJenkinsFixture.SyntheticPlugin delegate;

        /**
         * Creates a new synthetic plugin builder.
         * @see RealJenkinsRule#addSyntheticPlugin
         * @see RealJenkinsRule#createSyntheticPlugin
         * @param exampleClass an example of a class from the Java package containing any classes and resources you want included
         */
        public SyntheticPlugin(Class<?> exampleClass) {
            delegate = new RealJenkinsFixture.SyntheticPlugin(exampleClass);
        }

        /**
         * Creates a new synthetic plugin builder.
         * @see RealJenkinsRule#addSyntheticPlugin
         * @see RealJenkinsRule#createSyntheticPlugin
         * @param pkg the Java package containing any classes and resources you want included
         */
        public SyntheticPlugin(Package pkg) {
            delegate = new RealJenkinsFixture.SyntheticPlugin(pkg.getName());
        }

        /**
         * Creates a new synthetic plugin builder.
         * @see RealJenkinsRule#addSyntheticPlugin
         * @see RealJenkinsRule#createSyntheticPlugin
         * @param pkg the name of a Java package containing any classes and resources you want included
         */
        public SyntheticPlugin(String pkg) {
            delegate = new RealJenkinsFixture.SyntheticPlugin(pkg);
        }

        /**
         * Plugin identifier ({@code Short-Name} manifest header).
         * Defaults to being calculated from the package name,
         * replacing {@code .} with {@code -} and prefixed by {@code synthetic-}.
         */
        public SyntheticPlugin shortName(String shortName) {
            delegate.shortName(shortName);
            return this;
        }

        /**
         * Plugin version string ({@code Plugin-Version} manifest header).
         * Defaults to an arbitrary snapshot version.
         */
        public SyntheticPlugin version(String version) {
            delegate.version(version);
            return this;
        }

        /**
         * Add an extra plugin manifest header.
         * Examples:
         * <ul>
         * <li>{@code Jenkins-Version: 2.387.3}
         * <li>{@code Plugin-Dependencies: structs:325.vcb_307d2a_2782,support-core:1356.vd0f980edfa_46;resolution:=optional}
         * <li>{@code Long-Name: My Plugin}
         * </ul>
         */
        public SyntheticPlugin header(String key, String value) {
            delegate.header(key, value);
            return this;
        }
    }
}
