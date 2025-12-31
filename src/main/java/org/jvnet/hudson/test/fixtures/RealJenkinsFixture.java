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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.NamingThreadFactory;
import hudson.util.StreamCopyThread;
import io.jenkins.test.fips.FIPSTestBundleProvider;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.test.https.KeyStoreManager;
import jenkins.util.Timer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.htmlunit.WebClient;
import org.junit.AssumptionViolatedException;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.ErrorCollector;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.jvnet.hudson.test.HudsonHomeLoader;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.PluginUtils;
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.TailLog;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;
import org.jvnet.hudson.test.TestEnvironment;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.WarExploder;
import org.jvnet.hudson.test.XStreamSerializable;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;
import org.opentest4j.TestAbortedException;

/**
 * Like {@link JenkinsSessionFixture} but running Jenkins in a more realistic environment.
 * <p>Though Jenkins is run in a separate JVM using Winstone ({@code java -jar jenkins.war}),
 * you can still do “whitebox” testing: directly calling Java API methods, starting from {@link JenkinsRule} or not.
 * This is because the test code gets sent to the remote JVM and loaded and run there.
 * (Thus when using Maven, there are at least <em>three</em> JVMs involved:
 * Maven itself; the Surefire booter with your top-level test code; and the Jenkins controller with test bodies.)
 * Just as with {@link JenkinsRule}, all plugins found in the test classpath will be enabled,
 * but with more realistic behavior: class loaders in a graph, {@code pluginFirstClassLoader} and {@code maskClasses}, etc.
 * <p>“Compile-on-save” style development works for classes and resources in the current plugin:
 * with a suitable IDE, you can edit a source file, have it be sent to {@code target/classes/},
 * and rerun a test without needing to go through a full Maven build cycle.
 * This is because {@code target/test-classes/the.hpl} is used to load unpacked plugin resources.
 * <p>Like {@link JenkinsRule}, the controller is started in “development mode”:
 * the setup wizard is suppressed, the update center is not checked, etc.
 * Usage: <pre>{@code
 * private static final RealJenkinsFixture FIXTURE = new RealJenkinsFixture();
 *
 * public void method() {
 *     try {
 *         FIXTURE.setUp([…]);
 *         FIXTURE.runRemotely(() -> […]);
 *     } finally {
 *         FIXTURE.tearDown();
 *     }
 * }
 * }</pre>
 *
 * <p>Known limitations:
 * <ul>
 * <li>Execution is a bit slower due to the overhead of launching a new JVM; and class loading overhead cannot be shared between test cases. More memory is needed.
 * <li>Remote calls must be serializable. Use methods like {@link #runRemotely(RealJenkinsFixture.StepWithReturnAndOneArg, Serializable)} and/or {@link XStreamSerializable} as needed.
 * <li>{@code static} state cannot be shared between the top-level test code and test bodies (though the compiler will not catch this mistake).
 * <li>When using a snapshot dep on Jenkins core, you must build {@code jenkins.war} to test core changes (there is no “compile-on-save” support for this).
 * <li>{@link TestExtension} is not available (but try {@link #addSyntheticPlugin}).
 * <li>{@link LoggerRule} is not available, however additional loggers can be configured via {@link #withLogger(Class, Level)}}.
 * <li>{@link BuildWatcherFixture} is not available, but you can use {@link TailLog} instead.
 * </ul>
 *
 * @see JenkinsRule
 * @see JenkinsSessionFixture
 * @see org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension
 * @see org.jvnet.hudson.test.RealJenkinsRule
 * @see RealJenkinsFixtureInit
 */
@SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD", justification = "irrelevant")
public class RealJenkinsFixture {

    private static final Logger LOGGER = Logger.getLogger(RealJenkinsFixture.class.getName());

    private static final String REAL_JENKINS_FIXTURE_LOGGING = "RealJenkinsFixture.logging.";

    private final TemporaryDirectoryAllocator tmp = new TemporaryDirectoryAllocator();

    /**
     * JENKINS_HOME dir, consistent across restarts.
     */
    private AtomicReference<File> home;

    /**
     * TCP/IP port that the server is listening on.
     * <p>
     * Before the first start, it will be 0. Once started, it is set to the actual port Jenkins is listening to.
     * <p>
     * Like the home directory, this will be consistent across restarts.
     */
    private int port;

    private String httpListenAddress = InetAddress.getLoopbackAddress().getHostAddress();

    private File war;

    private String javaHome;

    private boolean includeTestClasspathPlugins = true;

    private final String token = UUID.randomUUID().toString();

    private final Set<String> extraPlugins = new TreeSet<>();

    private final List<SyntheticPlugin> syntheticPlugins = new ArrayList<>();

    private final Set<String> skippedPlugins = new TreeSet<>();

    private final List<String> javaOptions = new ArrayList<>();

    private final List<String> jenkinsOptions = new ArrayList<>();

    private final Map<String, String> extraEnv = new TreeMap<>();

    private int timeout = Integer.getInteger("jenkins.test.timeout", new DisableOnDebug(null).isDebugging() ? 0 : 600);

    private String host = "localhost";

    private Process proc;

    private final Map<String, Level> loggers = new HashMap<>();

    private int debugPort = 0;
    private boolean debugServer = true;
    private boolean debugSuspend;

    private boolean prepareHomeLazily;
    private boolean provisioned;
    private final List<File> bootClasspathFiles = new ArrayList<>();

    // TODO may need to be relaxed for Gradle-based plugins
    private static final Pattern SNAPSHOT_INDEX_JELLY = Pattern.compile("(file:/.+/target)/classes/index.jelly");

    private final PrefixedOutputStream.Builder prefixedOutputStreamBuilder = PrefixedOutputStream.builder();
    private boolean https;
    private KeyStoreManager keyStoreManager;
    private SSLSocketFactory sslSocketFactory;
    private X509Certificate rootCA;

    @NonNull
    private String prefix = "/jenkins";

    private Description description;

    public RealJenkinsFixture() {
        home = new AtomicReference<>();
    }

    /**
     * Links this extension to another, with {@link #getHome} to be initialized by whichever copy starts first.
     * Also copies configuration related to the setup of that directory:
     * {@link #includeTestClasspathPlugins(boolean)}, {@link #addPlugins}, {@link #addSyntheticPlugin}, and {@link #omitPlugins}.
     * Other configuration such as {@link #javaOptions(String...)} may be applied to both, but that is your choice.
     */
    public RealJenkinsFixture(RealJenkinsFixture source) {
        this.home = source.home;
        this.includeTestClasspathPlugins = source.includeTestClasspathPlugins;
        this.extraPlugins.addAll(source.extraPlugins);
        this.syntheticPlugins.addAll(source.syntheticPlugins);
        this.skippedPlugins.addAll(source.skippedPlugins);
    }

    /**
     * Add some plugins to the test classpath.
     *
     * @param plugins Filenames of the plugins to install. These are expected to be absolute test classpath resources,
     *                such as {@code plugins/workflow-job.hpi} for example.
     *                <p>For small fake plugins built for this purpose and exercising some bit of code, use {@link #addSyntheticPlugin}.
     *                If you wish to test with larger archives of real plugins, this is possible for example by
     *                binding {@code dependency:copy} to the {@code process-test-resources} phase.
     *                <p>In most cases you do not need this method. Simply add whatever plugins you are
     *                interested in testing against to your POM in {@code test} scope. These, and their
     *                transitive dependencies, will be loaded in all {@link RealJenkinsFixture} tests. This method
     *                is useful if only a particular test may load the tested plugin, or if the tested plugin
     *                is not available in a repository for use as a test dependency.
     */
    public RealJenkinsFixture addPlugins(String... plugins) {
        extraPlugins.addAll(List.of(plugins));
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
     * These will then be loaded only in {@link RealJenkinsFixture}-based tests requesting this plugin.
     *
     * @param plugin the configured {@link SyntheticPlugin}
     */
    public RealJenkinsFixture addSyntheticPlugin(SyntheticPlugin plugin) {
        syntheticPlugins.add(plugin);
        return this;
    }

    /**
     * Creates a test-only plugin based on sources defined in this module, but does not install it.
     * <p>See {@link #addSyntheticPlugin} for more details. Prefer that method if you simply want the
     * plugin to be installed automatically.
     *
     * @param plugin the configured {@link SyntheticPlugin}
     * @return the JPI file for the plugin
     * @see #addSyntheticPlugin
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "irrelevant, this is test code")
    public File createSyntheticPlugin(SyntheticPlugin plugin) throws IOException, URISyntaxException {
        File pluginJpi = new File(tmp.allocate("synthetic-plugin"), plugin.shortName + ".jpi");
        if (war == null) {
            throw new IllegalStateException("createSyntheticPlugin may only be invoked from within a test method");
        }
        try (JarFile jf = new JarFile(war)) {
            String jenkinsVersion = jf.getManifest().getMainAttributes().getValue("Jenkins-Version");
            plugin.writeTo(pluginJpi, jenkinsVersion);
        }
        return pluginJpi;
    }

    /**
     * Omit some plugins in the test classpath.
     *
     * @param plugins one or more code names, like {@code token-macro}
     */
    public RealJenkinsFixture omitPlugins(String... plugins) {
        skippedPlugins.addAll(List.of(plugins));
        return this;
    }

    /**
     * Add some JVM startup options.
     *
     * @param options one or more options, like {@code -Dorg.jenkinsci.Something.FLAG=true}
     */
    public RealJenkinsFixture javaOptions(String... options) {
        javaOptions.addAll(List.of(options));
        return this;
    }

    /**
     * Add some Jenkins (including Winstone) startup options.
     * You probably meant to use {@link #javaOptions(String...)}.
     *
     * @param options one or more options, like {@code --webroot=/tmp/war --pluginroot=/tmp/plugins}
     */
    public RealJenkinsFixture jenkinsOptions(String... options) {
        jenkinsOptions.addAll(List.of(options));
        return this;
    }

    /**
     * Set an extra environment variable.
     *
     * @param value null to cancel a previously set variable
     */
    public RealJenkinsFixture extraEnv(String key, String value) {
        extraEnv.put(key, value);
        return this;
    }

    /**
     * Adjusts the test timeout.
     * The timer starts when {@link #startJenkins} completes and {@link #runRemotely} is ready.
     * The default is currently set to 600 (10m).
     *
     * @param timeout number of seconds before exiting, or zero to disable
     */
    public RealJenkinsFixture withTimeout(int timeout) {
        this.timeout = timeout;
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
     * When using HTTPS, use {@link #https(String, KeyStoreManager, X509Certificate)} instead.
     */
    public RealJenkinsFixture withHost(String host) {
        if (https) {
            throw new IllegalStateException("Don't call this method when using HTTPS");
        }
        this.host = host;
        return this;
    }

    /**
     * Sets a custom prefix for the Jenkins root URL.
     * <p>
     * By default, the prefix defaults to {@code /jenkins}.
     * <p>
     * If not empty, must start with '/' and not end with '/'.
     */
    public RealJenkinsFixture withPrefix(@NonNull String prefix) {
        if (!prefix.isEmpty()) {
            if (!prefix.startsWith("/")) {
                throw new IllegalArgumentException("Prefix must start with a leading slash.");
            }
            if (prefix.endsWith("/")) {
                throw new IllegalArgumentException("Prefix must not end with a trailing slash.");
            }
        }
        this.prefix = prefix;
        return this;
    }

    /**
     * Sets a custom WAR file to be used by the extension instead of the one in the path or {@code war/target/jenkins.war} in case of core.
     */
    public RealJenkinsFixture withWar(File war) {
        this.war = war;
        return this;
    }

    /**
     * Allows to specify a java home, defaults to JAVA_HOME if not used
     */
    public RealJenkinsFixture withJavaHome(String javaHome) {
        this.javaHome = javaHome;
        return this;
    }

    public RealJenkinsFixture withLogger(Class<?> clazz, Level level) {
        return withLogger(clazz.getName(), level);
    }

    public RealJenkinsFixture withPackageLogger(Class<?> clazz, Level level) {
        return withLogger(clazz.getPackageName(), level);
    }

    public RealJenkinsFixture withLogger(String logger, Level level) {
        this.loggers.put(logger, level);
        return this;
    }

    /**
     * Sets a name for this instance, which will be prefixed to log messages to simplify debugging.
     */
    public RealJenkinsFixture withName(String name) {
        prefixedOutputStreamBuilder.withName(name);
        return this;
    }

    public String getName() {
        return prefixedOutputStreamBuilder.getName();
    }

    /**
     * Applies ANSI coloration to log lines produced by this instance, complementing {@link #withName}.
     * Ignored when on CI.
     */
    public RealJenkinsFixture withColor(PrefixedOutputStream.AnsiColor color) {
        prefixedOutputStreamBuilder.withColor(color);
        return this;
    }

    /**
     * Provides a custom fixed port instead of a random one.
     *
     * @param port a custom port to use instead of a random one.
     */
    public RealJenkinsFixture withPort(int port) {
        this.port = port;
        return this;
    }

    /**
     * Provides a custom interface to listen to.
     * <p><em>Important:</em> for security reasons this should be overridden only in special scenarios,
     * such as testing inside a Docker container.
     * Otherwise a developer running tests could inadvertently expose a Jenkins service without password protection,
     * allowing remote code execution.
     *
     * @param httpListenAddress network interface such as <pre>0.0.0.0</pre>. Defaults to <pre>127.0.0.1</pre>.
     */
    public RealJenkinsFixture withHttpListenAddress(String httpListenAddress) {
        this.httpListenAddress = httpListenAddress;
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
     * public RealJenkinsFixture cc1 = new RealJenkinsFixture().withName("cc1").withDebugPort(4001).withDebugServer(false);
     *
     * public RealJenkinsFixture cc2 = new RealJenkinsFixture().withName("cc2").withDebugPort(4002).withDebugServer(false);
     * </pre>
     * <p>
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
    public RealJenkinsFixture withDebugPort(int debugPort) {
        if (debugPort < 0) {
            throw new IllegalArgumentException("debugPort must be positive");
        }
        if (debugPort >= 65536) {
            throw new IllegalArgumentException("debugPort must be a valid TCP port (< 65536)");
        }
        this.debugPort = debugPort;
        return this;
    }

    /**
     * Allows to use debug in server mode or client mode. Client mode is friendlier to controller restarts.
     *
     * @param debugServer true to use server=y, false to use server=n
     * @see #withDebugPort(int)
     */
    public RealJenkinsFixture withDebugServer(boolean debugServer) {
        this.debugServer = debugServer;
        return this;
    }

    /**
     * Whether to suspend the controller VM on startup until debugger is connected. Defaults to false.
     *
     * @param debugSuspend true to suspend the controller VM on startup until debugger is connected.
     */
    public RealJenkinsFixture withDebugSuspend(boolean debugSuspend) {
        this.debugSuspend = debugSuspend;
        return this;
    }

    /**
     * The intended use case for this is to use the plugins bundled into the war {@link RealJenkinsFixture#withWar(File)}
     * instead of the plugins in the pom. A typical scenario for this feature is a test which does not live inside a
     * plugin's src/test/java
     *
     * @param includeTestClasspathPlugins false if plugins from pom should not be used (default true)
     */
    public RealJenkinsFixture includeTestClasspathPlugins(boolean includeTestClasspathPlugins) {
        this.includeTestClasspathPlugins = includeTestClasspathPlugins;
        return this;
    }

    /**
     * Allows {@code JENKINS_HOME} initialization to be delayed until {@link #startJenkins} is called for the first time.
     * <p>
     * This allows methods such as {@link #addPlugins} to be called dynamically inside of test methods, which enables
     * related tests that need to configure {@link RealJenkinsFixture} in different ways to be defined in the same class
     * using only a single instance of {@link RealJenkinsFixture}.
     */
    public RealJenkinsFixture prepareHomeLazily(boolean prepareHomeLazily) {
        this.prepareHomeLazily = prepareHomeLazily;
        return this;
    }

    /**
     * Use {@link #withFIPSEnabled(FIPSTestBundleProvider)}  with default value of {@link FIPSTestBundleProvider#get()}
     */
    public RealJenkinsFixture withFIPSEnabled() {
        return withFIPSEnabled(FIPSTestBundleProvider.get());
    }

    /**
     * @param fipsTestBundleProvider the {@link FIPSTestBundleProvider} to use for testing
     */
    public RealJenkinsFixture withFIPSEnabled(FIPSTestBundleProvider fipsTestBundleProvider) {
        Objects.requireNonNull(fipsTestBundleProvider, "fipsTestBundleProvider must not be null");
        try {
            return withBootClasspath(
                            fipsTestBundleProvider.getBootClasspathFiles().toArray(new File[0]))
                    .javaOptions(fipsTestBundleProvider.getJavaOptions().toArray(new String[0]));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param files add some {@link File} to bootclasspath
     */
    public RealJenkinsFixture withBootClasspath(File... files) {
        this.bootClasspathFiles.addAll(List.of(files));
        return this;
    }

    public static List<String> getJacocoAgentOptions() {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMxBean.getInputArguments();
        return arguments.stream()
                .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("jacoco"))
                .toList();
    }

    public void setUp(Description description) throws Exception {
        this.description = description;
        jenkinsOptions(
                "--webroot=" + createTempDirectory("webroot"), "--pluginroot=" + createTempDirectory("pluginroot"));
        if (war == null) {
            war = findJenkinsWar();
        }
        if (home.get() == null) {
            home.set(tmp.allocate());
            if (!prepareHomeLazily) {
                provision();
            }
        }
    }

    public void tearDown() throws Exception {
        stopJenkins();

        try {
            tmp.dispose();
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
        }
    }

    /**
     * Initializes {@code JENKINS_HOME}, but does not start Jenkins.
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "irrelevant")
    private void provision() throws Exception {
        provisioned = true;
        if (home.get() == null) {
            home.set(tmp.allocate());
        }
        try {
            if (description == null) {
                throw new IllegalStateException("RealJenkinsFixture must be initialized via #setUp");
            }
            Method method = description.getTestClass().getDeclaredMethod(description.getMethodName());
            LocalData localData = description.getAnnotation(LocalData.class);
            if (localData != null) {
                new HudsonHomeLoader.Local(method, localData.value()).copy(getHome());
            }
        } catch (NoSuchMethodException e) {
            //
        }

        File plugins = new File(getHome(), "plugins");
        Files.createDirectories(plugins.toPath());
        // set the version to the version of jenkins used for testing to avoid dragging in detached plugins
        String targetJenkinsVersion;
        try (JarFile jf = new JarFile(war)) {
            targetJenkinsVersion = jf.getManifest().getMainAttributes().getValue("Jenkins-Version");
            PluginUtils.createRealJenkinsFixturePlugin(plugins, targetJenkinsVersion);
        }

        if (includeTestClasspathPlugins) {
            // Adapted from UnitTestSupportingPluginManager & JenkinsRule.recipeLoadCurrentPlugin:
            Set<String> snapshotPlugins = new TreeSet<>();
            Enumeration<URL> indexJellies =
                    RealJenkinsFixture.class.getClassLoader().getResources("index.jelly");
            while (indexJellies.hasMoreElements()) {
                String indexJelly = indexJellies.nextElement().toString();
                Matcher m = SNAPSHOT_INDEX_JELLY.matcher(indexJelly);
                if (m.matches()) {
                    Path snapshotManifest;
                    snapshotManifest = Paths.get(URI.create(m.group(1) + "/test-classes/the.jpl"));
                    if (!Files.exists(snapshotManifest)) {
                        snapshotManifest = Paths.get(URI.create(m.group(1) + "/test-classes/the.hpl"));
                    }
                    if (Files.exists(snapshotManifest)) {
                        String shortName;
                        try (InputStream is = Files.newInputStream(snapshotManifest)) {
                            shortName = new Manifest(is).getMainAttributes().getValue("Short-Name");
                        }
                        if (shortName == null) {
                            throw new IOException("malformed " + snapshotManifest);
                        }
                        if (skippedPlugins.contains(shortName)) {
                            continue;
                        }
                        // Not totally realistic, but test phase is run before package phase. TODO can we add an option
                        // to run in integration-test phase?
                        Files.copy(snapshotManifest, plugins.toPath().resolve(shortName + ".jpl"));
                        snapshotPlugins.add(shortName);
                    } else {
                        System.err.println("Warning: found " + indexJelly
                                + " but did not find corresponding ../test-classes/the.[hj]pl");
                    }
                } else {
                    // Do not warn about the common case of jar:file:/**/.m2/repository/**/*.jar!/index.jelly
                }
            }
            URL index = RealJenkinsFixture.class.getResource("/test-dependencies/index");
            if (index != null) {
                try (BufferedReader r =
                        new BufferedReader(new InputStreamReader(index.openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (snapshotPlugins.contains(line) || skippedPlugins.contains(line)) {
                            continue;
                        }
                        final URL url = new URL(index, line + ".jpi");
                        File f;
                        try {
                            f = new File(url.toURI());
                        } catch (IllegalArgumentException x) {
                            if (x.getMessage().equals("URI is not hierarchical")) {
                                throw new IOException(
                                        "You are probably trying to load plugins from within a jarfile (not possible). If"
                                                + " you are running this in your IDE and see this message, it is likely"
                                                + " that you have a clean target directory. Try running 'mvn test-compile' "
                                                + "from the command line (once only), which will copy the required plugins "
                                                + "into target/test-classes/test-dependencies - then retry your test",
                                        x);
                            } else {
                                throw new IOException(index + " contains bogus line " + line, x);
                            }
                        }
                        if (f.exists()) {
                            FileUtils.copyURLToFile(url, new File(plugins, line + ".jpi"));
                        } else {
                            FileUtils.copyURLToFile(new URL(index, line + ".hpi"), new File(plugins, line + ".jpi"));
                        }
                    }
                }
            }
        }
        for (String extraPlugin : extraPlugins) {
            URL url = RealJenkinsFixture.class.getClassLoader().getResource(extraPlugin);
            String name;
            try (InputStream is = url.openStream();
                    JarInputStream jis = new JarInputStream(is)) {
                Manifest man = jis.getManifest();
                if (man == null) {
                    throw new IOException("No manifest found in " + extraPlugin);
                }
                name = man.getMainAttributes().getValue("Short-Name");
                if (name == null) {
                    throw new IOException("No Short-Name found in " + extraPlugin);
                }
            }
            FileUtils.copyURLToFile(url, new File(plugins, name + ".jpi"));
        }
        for (SyntheticPlugin syntheticPlugin : syntheticPlugins) {
            syntheticPlugin.writeTo(new File(plugins, syntheticPlugin.shortName + ".jpi"), targetJenkinsVersion);
        }
        System.err.println("Will load plugins: "
                + Stream.of(plugins.list())
                        .filter(n -> n.matches(".+[.][hj]p[il]"))
                        .sorted()
                        .collect(Collectors.joining(" ")));
    }

    /**
     * Deletes {@code JENKINS_HOME}.
     * <p>
     * This method does not need to be invoked when using {@code @Rule} or {@code @ClassRule} to run {@code RealJenkinsRule}.
     */
    public void deprovision() throws Exception {
        tmp.dispose();
        home.set(null);
        provisioned = false;
    }

    /**
     * Creates a temporary directory.
     * Unlike {@link Files#createTempDirectory(String, FileAttribute...)}
     * this will be cleaned up after the test exits (like {@link TemporaryFolder}),
     * and will honor {@code java.io.tmpdir} set by Surefire
     * after {@code StaticProperty.JAVA_IO_TMPDIR} has been initialized.
     */
    public Path createTempDirectory(String prefix) throws IOException {
        return tmp.allocate(prefix).toPath();
    }

    /**
     * Returns true if the Jenkins process is alive.
     */
    public boolean isAlive() {
        return proc != null && proc.isAlive();
    }

    /**
     * Returns the Jenkins process handle .
     * @return the process handle
     */
    public Process getProcess() {
        return proc;
    }

    public String[] getTruststoreJavaOptions() {
        return keyStoreManager != null ? keyStoreManager.getTruststoreJavaOptions() : new String[0];
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
    public interface Step extends Serializable {
        void run(JenkinsRule r) throws Throwable;
    }

    @FunctionalInterface
    public interface Step2<T extends Serializable> extends Serializable {
        T run(JenkinsRule r) throws Throwable;
    }

    /**
     * Run one Jenkins session, send one or more test thunks, and shut down.
     */
    public void then(Step... steps) throws Throwable {
        then(new StepsToStep2(steps));
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
        if (port == 0) {
            throw new IllegalStateException("This method must be called after calling #startJenkins.");
        }
        return new URL(https ? "https" : "http", host, port, prefix + "/");
    }

    /**
     * Sets up HTTPS for the current instance, and disables plain HTTP.
     * This generates a self-signed certificate for <em>localhost</em>. The corresponding root CA that needs to be trusted by HTTP client can be obtained using {@link #getRootCA()}.
     *
     * @return the current instance
     * @see #createWebClient()
     */
    public RealJenkinsFixture https() {
        try {
            var keyStorePath = tmp.allocate().toPath().resolve("test-keystore.p12");
            IOUtils.copy(getClass().getResource("/https/test-keystore.p12"), keyStorePath.toFile());
            var keyStoreManager = new KeyStoreManager(keyStorePath, "changeit");
            try (var is = getClass().getResourceAsStream("/https/test-cert.pem")) {
                var cert = (X509Certificate)
                        CertificateFactory.getInstance("X.509").generateCertificate(is);
                https("localhost", keyStoreManager, cert);
            }
        } catch (CertificateException | KeyStoreException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /**
     * Sets up HTTPS for the current instance, and disables plain HTTP.
     * <p>
     * You don't need to call {@link #withHost(String)} when calling this method.
     *
     * @param host            the host name to use in the certificate
     * @param keyStoreManager a key store manager containing the key and certificate to use for HTTPS. It needs to be valid for the given host
     * @param rootCA          the certificate that needs to be trusted by callers.
     * @return the current instance
     * @see #createWebClient()
     * @see #withHost(String)
     */
    public RealJenkinsFixture https(
            @NonNull String host, @NonNull KeyStoreManager keyStoreManager, @NonNull X509Certificate rootCA) {
        this.host = host;
        this.https = true;
        this.keyStoreManager = keyStoreManager;
        try {
            this.sslSocketFactory = keyStoreManager.buildClientSSLContext().getSocketFactory();
        } catch (NoSuchAlgorithmException
                | KeyManagementException
                | CertificateException
                | KeyStoreException
                | IOException e) {
            throw new RuntimeException(e);
        }
        this.rootCA = rootCA;
        return this;
    }

    /**
     * @return the current autogenerated root CA or null if {@link #https()} has not been called.
     */
    @Nullable
    public X509Certificate getRootCA() {
        return rootCA;
    }

    /**
     * Returns the autogenerated self-signed root CA in PEM format, or null if {@link #https()} has not been called.
     * Typically used to configure {@link InboundAgentFixture.Options.Builder#cert}.
     *
     * @return the root CA in PEM format, or null if unavailable
     */
    @Nullable
    public String getRootCAPem() {
        if (rootCA == null) {
            return null;
        }
        try (var is = getClass().getResourceAsStream("/https/test-cert.pem")) {
            assert is != null;
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds a {@link SSLContext} trusting the current instance.
     */
    @NonNull
    public SSLContext buildSSLContext() throws NoSuchAlgorithmException {
        if (rootCA != null) {
            try {
                var myTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                myTrustStore.load(null, null);
                myTrustStore.setCertificateEntry(
                        getName() != null ? getName() : UUID.randomUUID().toString(), rootCA);
                var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(myTrustStore);
                var context = SSLContext.getInstance("TLS");
                context.init(null, trustManagerFactory.getTrustManagers(), null);
                return context;
            } catch (CertificateException | KeyManagementException | IOException | KeyStoreException e) {
                throw new RuntimeException(e);
            }
        } else {
            return SSLContext.getDefault();
        }
    }

    private URL endpoint(String method) throws MalformedURLException {
        return new URL(getUrl(), "RealJenkinsFixture/" + method + "?token=" + token);
    }

    /**
     * Obtains the Jenkins home directory.
     * Normally it will suffice to use {@link LocalData} to populate files.
     */
    public File getHome() {
        return home.get();
    }

    /**
     * Switch the Jenkins home directory.
     * Will affect subsequent startups of this extension,
     * but not other copies linked via {@link RealJenkinsFixture#RealJenkinsFixture(RealJenkinsFixture)}.
     * Normally unnecessary but could be used to simulate running on the wrong home.
     */
    public void setHome(File newHome) {
        home = new AtomicReference<>(newHome);
    }

    private static File findJenkinsWar() throws Exception {
        // Adapted from WarExploder.explode

        // Are we in Jenkins core? If so, pick up "war/target/jenkins.war".
        File d = new File(".").getAbsoluteFile();
        for (; d != null; d = d.getParentFile()) {
            if (new File(d, ".jenkins").exists()) {
                File war = new File(d, "war/target/jenkins.war");
                if (war.exists()) {
                    LOGGER.log(Level.INFO, "Using jenkins.war from {0}", war);
                    return war;
                }
            }
        }

        return WarExploder.findJenkinsWar();
    }

    /**
     * Create a client configured to trust any self-signed certificate used by this instance.
     */
    public WebClient createWebClient() {
        var wc = new WebClient();
        if (keyStoreManager != null) {
            keyStoreManager.configureWebClient(wc);
        }
        return wc;
    }

    @SuppressFBWarnings(
            value = {"PATH_TRAVERSAL_IN", "URLCONNECTION_SSRF_FD", "COMMAND_INJECTION"},
            justification = "irrelevant")
    public void startJenkins() throws Exception {
        Path portFile;
        if (proc != null) {
            throw new IllegalStateException("Jenkins is (supposedly) already running");
        }
        if (prepareHomeLazily && !provisioned) {
            provision();
        }
        var metadata = createTempDirectory("RealJenkinsFixture");
        var cpFile = metadata.resolve("cp.txt");
        String cp = System.getProperty("java.class.path");
        Files.writeString(
                cpFile,
                Stream.of(cp.split(File.pathSeparator)).collect(Collectors.joining(System.lineSeparator())),
                StandardCharsets.UTF_8);
        List<String> argv = new ArrayList<>(List.of(
                new File(javaHome != null ? javaHome : System.getProperty("java.home"), "bin/java").getAbsolutePath(),
                "-ea",
                "-Dhudson.Main.development=true",
                "-DRealJenkinsFixture.classpath=" + cpFile,
                "-DRealJenkinsFixture.location="
                        + RealJenkinsFixture.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation(),
                "-DRealJenkinsFixture.description=" + description.getDisplayName(),
                "-DRealJenkinsFixture.token=" + token));
        argv.addAll(getJacocoAgentOptions());
        for (Map.Entry<String, Level> e : loggers.entrySet()) {
            argv.add("-D" + REAL_JENKINS_FIXTURE_LOGGING + e.getKey() + "="
                    + e.getValue().getName());
        }
        portFile = metadata.resolve("jenkins-port.txt");
        argv.add("-Dwinstone.portFileName=" + portFile);
        var tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null) {
            argv.add("-Djava.io.tmpdir=" + tmp);
        }
        boolean debugging = new DisableOnDebug(null).isDebugging();
        if (debugging) {
            argv.add("-agentlib:jdwp=transport=dt_socket"
                    + ",server=" + (debugServer ? "y" : "n")
                    + ",suspend=" + (debugSuspend ? "y" : "n")
                    + (debugPort > 0 ? ",address=" + httpListenAddress + ":" + debugPort : ""));
        }
        if (!bootClasspathFiles.isEmpty()) {
            String fileList = bootClasspathFiles.stream()
                    .map(File::getAbsolutePath)
                    .collect(Collectors.joining(File.pathSeparator));
            argv.add("-Xbootclasspath/a:" + fileList);
        }
        argv.addAll(javaOptions);

        argv.addAll(List.of(
                "-jar", war.getAbsolutePath(), "--enable-future-java", "--httpListenAddress=" + httpListenAddress));
        if (!prefix.isEmpty()) {
            argv.add("--prefix=" + prefix);
        }
        argv.addAll(getPortOptions());
        if (https) {
            argv.add("--httpsKeyStore=" + keyStoreManager.getPath().toAbsolutePath());
            if (keyStoreManager.getPassword() != null) {
                argv.add("--httpsKeyStorePassword=" + keyStoreManager.getPassword());
            }
        }
        argv.addAll(jenkinsOptions);
        Map<String, String> env = new TreeMap<>();
        env.put("JENKINS_HOME", getHome().getAbsolutePath());
        String forkNumber = System.getProperty("surefire.forkNumber");
        if (forkNumber != null) {
            // https://maven.apache.org/surefire/maven-surefire-plugin/examples/fork-options-and-parallel-execution.html#forked-test-execution
            // Otherwise accessible only to the Surefire JVM, not to the Jenkins controller JVM.
            env.put("SUREFIRE_FORK_NUMBER", forkNumber);
        }
        for (Map.Entry<String, String> entry : extraEnv.entrySet()) {
            if (entry.getValue() != null) {
                env.put(entry.getKey(), entry.getValue());
            }
        }
        // TODO escape spaces like Launcher.printCommandLine, or LabelAtom.escape (beware that
        // QuotedStringTokenizer.quote(String) Javadoc is untrue):
        System.err.println(env.entrySet().stream().map(Map.Entry::toString).collect(Collectors.joining(" ")) + " "
                + String.join(" ", argv));
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.environment().putAll(env);
        // TODO options to set Winstone options, etc.
        // TODO pluggable launcher interface to support a Dockerized Jenkins JVM
        pb.redirectErrorStream(true);
        proc = pb.start();
        new StreamCopyThread(
                        description.getDisplayName(),
                        proc.getInputStream(),
                        prefixedOutputStreamBuilder.build(System.err))
                .start();
        int tries = 0;
        while (true) {
            if (!proc.isAlive()) {
                int exitValue = proc.exitValue();
                proc = null;
                throw new IOException("Jenkins process terminated prematurely with exit code " + exitValue);
            }
            if (port == 0 && Files.isRegularFile(portFile)) {
                port = readPort(portFile);
            }
            if (port != 0) {
                try {
                    URL status = endpoint("status");
                    HttpURLConnection conn = decorateConnection(status.openConnection());

                    String checkResult = checkResult(conn);
                    if (checkResult == null) {
                        System.err.println((getName() != null ? getName() : "Jenkins") + " is running at " + getUrl());
                        break;
                    } else {
                        throw new IOException("Response code " + conn.getResponseCode() + " for " + status + ": "
                                + checkResult + " " + conn.getHeaderFields());
                    }

                } catch (JenkinsStartupException jse) {
                    // Jenkins has completed startup but failed
                    // do not make any further attempts and kill the process
                    proc.destroyForcibly();
                    proc = null;
                    throw jse;
                } catch (Exception x) {
                    tries++;
                    if (!debugging && tries == /* 3m */ 1800) {
                        throw new AssertionError("Jenkins did not start after 3m");
                    } else if (tries % /* 1m */ 600 == 0) {
                        x.printStackTrace();
                    }
                }
            }
            Thread.sleep(100);
        }
        addTimeout();
    }

    private Collection<String> getPortOptions() {
        // Initially port=0. On subsequent runs, this is set to the port allocated randomly on the first run.
        if (https) {
            return List.of("--httpPort=-1", "--httpsPort=" + port);
        } else {
            return List.of("--httpPort=" + port);
        }
    }

    @CheckForNull
    public static String checkResult(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        if (code == 200) {
            conn.getInputStream().close();
            return null;
        } else {
            String err = "?";
            try (InputStream is = conn.getErrorStream()) {
                if (is != null) {
                    err = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception x) {
                x.printStackTrace();
            }
            if (code == 500) {
                throw new JenkinsStartupException(err);
            }
            return err;
        }
    }

    private void addTimeout() {
        if (timeout > 0) {
            Timer.get()
                    .schedule(
                            () -> {
                                if (proc != null) {
                                    LOGGER.warning("Test timeout expired, stopping steps…");
                                    try {
                                        decorateConnection(endpoint("timeout").openConnection())
                                                .getInputStream()
                                                .close();
                                    } catch (IOException x) {
                                        x.printStackTrace();
                                    }
                                    LOGGER.warning("…and giving steps a chance to fail…");
                                    try {
                                        Thread.sleep(15_000);
                                    } catch (InterruptedException x) {
                                        x.printStackTrace();
                                    }
                                    LOGGER.warning("…and killing Jenkins process.");
                                    proc.destroyForcibly();
                                    proc = null;
                                }
                            },
                            timeout,
                            TimeUnit.SECONDS);
        }
    }

    private static int readPort(Path portFile) throws IOException {
        String s = Files.readString(portFile, StandardCharsets.UTF_8);

        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new AssertionError("Unable to parse port from " + s + ". Jenkins did not start.");
        }
    }

    /**
     * Stops Jenkins and releases any system resources associated
     * with it. If Jenkins is already stopped then invoking this
     * method has no effect.
     */
    public void stopJenkins() throws Exception {
        if (proc != null) {
            Process _proc = proc;
            proc = null;
            if (_proc.isAlive()) {
                try {
                    decorateConnection(endpoint("exit").openConnection())
                            .getInputStream()
                            .close();
                } catch (IOException e) {
                    System.err.println("Unable to connect to the Jenkins process to stop it: " + e);
                }
            } else {
                System.err.println("Jenkins process was already terminated.");
            }
            if (!_proc.waitFor(60, TimeUnit.SECONDS)) {
                System.err.println("Jenkins failed to stop within 60 seconds, attempting to kill the Jenkins process");
                _proc.destroyForcibly();
                throw new AssertionError("Jenkins failed to terminate within 60 seconds");
            }
            int exitValue = _proc.exitValue();
            if (exitValue != 0) {
                throw new AssertionError("nonzero exit code: " + exitValue);
            }
        }
    }

    /**
     * Stops Jenkins abruptly, without giving it a chance to shut down cleanly.
     * If Jenkins is already stopped then invoking this method has no effect.
     */
    public void stopJenkinsForcibly() {
        if (proc != null) {
            var _proc = proc;
            proc = null;
            System.err.println("Killing the Jenkins process as requested");
            _proc.destroyForcibly();
        }
    }

    /**
     * Runs one or more steps on the remote system.
     * (Compared to multiple calls, passing a series of steps is slightly more efficient
     * as only one network call is made.)
     */
    public void runRemotely(Step... steps) throws Throwable {
        runRemotely(new StepsToStep2(steps));
    }

    /**
     * Run a step on the remote system.
     * Alias for {@link #runRemotely(RealJenkinsFixture.Step...)} (with one step)
     * that is easier to resolve for lambdas.
     */
    public void run(Step step) throws Throwable {
        runRemotely(step);
    }

    /**
     * Run a step on the remote system, but do not immediately fail, just record any error.
     * Same as {@link ErrorCollector#checkSucceeds} but more concise to call.
     */
    public void run(ErrorCollector errors, Step step) {
        errors.checkSucceeds(() -> {
            try {
                run(step);
                return null;
            } catch (Exception x) {
                throw x;
            } catch (Throwable x) {
                throw new Exception(x);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD", justification = "irrelevant")
    public <T extends Serializable> T runRemotely(Step2<T> s) throws Throwable {
        HttpURLConnection conn = decorateConnection(endpoint("step").openConnection());
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setDoOutput(true);

        Init.writeSer(conn.getOutputStream(), new InputPayload(token, s, getUrl()));
        try {
            OutputPayload result = (OutputPayload) Init.readSer(conn.getInputStream(), null);
            if (result.assumptionFailure != null) {
                if (result.error.getCause() instanceof TestAbortedException) {
                    throw new TestAbortedException(result.assumptionFailure, result.error);
                } else if (result.error.getCause() instanceof AssumptionViolatedException) {
                    throw new AssumptionViolatedException(result.assumptionFailure, result.error);
                } else {
                    throw new StepException(result.error, result.assumptionFailure);
                }
            } else if (result.error != null) {
                throw new StepException(result.error, getName());
            }
            return (T) result.result;
        } catch (IOException e) {
            try (InputStream is = conn.getErrorStream()) {
                if (is != null) {
                    String errorMessage = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    e.addSuppressed(new IOException("Response body: " + errorMessage));
                }
            } catch (IOException e2) {
                e.addSuppressed(e2);
            }
            throw e;
        }
    }

    /**
     * Run a step with a return value on the remote system.
     * Alias for {@link #runRemotely(RealJenkinsFixture.Step2)}
     * that is easier to resolve for lambdas.
     */
    public <T extends Serializable> T call(Step2<T> s) throws Throwable {
        return runRemotely(s);
    }

    private HttpURLConnection decorateConnection(@NonNull URLConnection urlConnection) {
        if (sslSocketFactory != null) {
            ((HttpsURLConnection) urlConnection).setSSLSocketFactory(sslSocketFactory);
        }
        return (HttpURLConnection) urlConnection;
    }

    @FunctionalInterface
    public interface StepWithOneArg<A1 extends Serializable> extends Serializable {
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
    public interface StepWithTwoArgs<A1 extends Serializable, A2 extends Serializable> extends Serializable {
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
            extends Serializable {
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
            extends Serializable {
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
    public interface StepWithReturnAndOneArg<R extends Serializable, A1 extends Serializable> extends Serializable {
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
            extends Serializable {
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
            extends Serializable {
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
            extends Serializable {
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

    // Should not refer to any types outside the JRE.
    public static final class Init {

        public static void run(Object jenkins) throws Exception {
            Object pluginManager = jenkins.getClass().getField("pluginManager").get(jenkins);
            ClassLoader uberClassLoader = (ClassLoader)
                    pluginManager.getClass().getField("uberClassLoader").get(pluginManager);
            ClassLoader tests = new URLClassLoader(
                    Files.readAllLines(
                                    Paths.get(System.getProperty("RealJenkinsFixture.classpath")),
                                    StandardCharsets.UTF_8)
                            .stream()
                            .map(Init::pathToURL)
                            .toArray(URL[]::new),
                    uberClassLoader);
            tests.loadClass("org.jvnet.hudson.test.fixtures.RealJenkinsFixture$Endpoint")
                    .getMethod("register")
                    .invoke(null);
        }

        @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "irrelevant")
        private static URL pathToURL(String path) {
            try {
                return Paths.get(path).toUri().toURL();
            } catch (MalformedURLException x) {
                throw new IllegalArgumentException(x);
            }
        }

        @SuppressWarnings("unused")
        static void writeSer(File f, Object o) throws Exception {
            try (OutputStream os = new FileOutputStream(f)) {
                writeSer(os, o);
            }
        }

        static void writeSer(OutputStream os, Object o) throws Exception {
            try (ObjectOutputStream oos = new ObjectOutputStream(os)) {
                oos.writeObject(o);
            }
        }

        @SuppressWarnings("unused")
        static Object readSer(File f, ClassLoader loader) throws Exception {
            try (InputStream is = new FileInputStream(f)) {
                return readSer(is, loader);
            }
        }

        @SuppressFBWarnings(value = "OBJECT_DESERIALIZATION", justification = "irrelevant")
        static Object readSer(InputStream is, ClassLoader loader) throws Exception {
            try (ObjectInputStream ois = new ObjectInputStream(is) {
                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                    if (loader != null) {
                        try {
                            return loader.loadClass(desc.getName());
                        } catch (ClassNotFoundException x) {
                        }
                    }
                    return super.resolveClass(desc);
                }
            }) {
                return ois.readObject();
            }
        }

        private Init() {}
    }

    public static final class Endpoint implements UnprotectedRootAction {
        @SuppressWarnings({"deprecation", "unused"})
        public static void register() throws Exception {
            Jenkins j = Jenkins.get();
            configureLogging();
            j.getActions().add(new Endpoint());
            CrumbExclusion.all().add(new CrumbExclusion() {
                @Override
                public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws IOException, ServletException {
                    if (request.getPathInfo().startsWith("/RealJenkinsFixture/")) {
                        chain.doFilter(request, response);
                        return true;
                    }
                    return false;
                }
            });
            JenkinsRule._configureUpdateCenter(j);
            System.err.println("RealJenkinsFixture ready");
            if (!new DisableOnDebug(null).isDebugging()) {
                Timer.get().scheduleAtFixedRate(JenkinsRule::dumpThreads, 2, 2, TimeUnit.MINUTES);
            }
        }

        private static final Set<Logger> LOGGERS = new HashSet<>();

        private static void configureLogging() {
            Level minLevel = Level.INFO;
            for (String propertyName : System.getProperties().stringPropertyNames()) {
                if (propertyName.startsWith(REAL_JENKINS_FIXTURE_LOGGING)) {
                    String loggerName = propertyName.substring(REAL_JENKINS_FIXTURE_LOGGING.length());
                    Logger logger = Logger.getLogger(loggerName);
                    Level level = Level.parse(System.getProperty(propertyName));
                    if (level.intValue() < minLevel.intValue()) {
                        minLevel = level;
                    }
                    logger.setLevel(level);
                    LOGGERS.add(
                            logger); // Keep a ref around, otherwise it is garbage collected and we lose configuration
                }
            }
            // Increase ConsoleHandler level to the finest level we want to log.
            if (!LOGGERS.isEmpty()) {
                for (Handler h : Logger.getLogger("").getHandlers()) {
                    if (h instanceof ConsoleHandler) {
                        h.setLevel(minLevel);
                    }
                }
            }
        }

        @Override
        public String getUrlName() {
            return "RealJenkinsFixture";
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        private final byte[] actualToken =
                System.getProperty("RealJenkinsFixture.token").getBytes(StandardCharsets.US_ASCII);

        private void checkToken(String token) {
            if (!MessageDigest.isEqual(actualToken, token.getBytes(StandardCharsets.US_ASCII))) {
                throw HttpResponses.forbidden();
            }
        }

        @SuppressWarnings("unused")
        public void doStatus(@QueryParameter String token) {
            System.err.println("Checking status");
            checkToken(token);
        }

        /**
         * Used to run test methods on a separate thread so that code that uses {@link Stapler#getCurrentRequest2}
         * does not inadvertently interact with the request for {@link #doStep} itself.
         */
        private static final ExecutorService STEP_RUNNER = Executors.newSingleThreadExecutor(new NamingThreadFactory(
                Executors.defaultThreadFactory(), RealJenkinsFixture.class.getName() + ".STEP_RUNNER"));

        @POST
        @SuppressWarnings("unused")
        public void doStep(StaplerRequest2 req, StaplerResponse2 rsp) throws Throwable {
            InputPayload input = (InputPayload) Init.readSer(req.getInputStream(), Endpoint.class.getClassLoader());
            checkToken(input.token);
            Step2<?> s = input.step;
            URL url = input.url;
            String contextPath = input.contextPath;

            Throwable err = null;
            Object object = null;
            try {
                object = STEP_RUNNER
                        .submit(() -> {
                            try (CustomJenkinsRule rule = new CustomJenkinsRule(url, contextPath);
                                    ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                                return s.run(rule);
                            } catch (Throwable t) {
                                throw new RuntimeException(t);
                            }
                        })
                        .get();
            } catch (ExecutionException e) {
                // Unwrap once for ExecutionException and once for RuntimeException:
                err = e.getCause().getCause();
            } catch (CancellationException | InterruptedException e) {
                err = e;
            }
            Init.writeSer(rsp.getOutputStream(), new OutputPayload(object, err));
        }

        @SuppressWarnings("unused")
        public HttpResponse doExit(@QueryParameter String token) throws IOException, InterruptedException {
            checkToken(token);
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                Jenkins j = Jenkins.get();
                j.doQuietDown(true, 30_000, null, false); // 30s < 60s timeout of stopJenkins
                // Cannot use doExit since it requires StaplerRequest2, so would throw an error on older cores:
                j.getLifecycle().onStop("RealJenkinsFixture", null);
                j.cleanUp();
                new Thread(() -> System.exit(0), "exiting").start();
            }
            return HttpResponses.ok();
        }

        @SuppressWarnings("unused")
        public void doTimeout(@QueryParameter String token) {
            checkToken(token);
            LOGGER.warning("Initiating shutdown");
            STEP_RUNNER.shutdownNow();
            try {
                LOGGER.warning("Awaiting termination of steps…");
                STEP_RUNNER.awaitTermination(30, TimeUnit.SECONDS);
                LOGGER.warning("…terminated.");
            } catch (InterruptedException x) {
                x.printStackTrace();
            }
        }
    }

    public static final class CustomJenkinsRule extends JenkinsRule implements AutoCloseable {
        private final URL url;

        public CustomJenkinsRule(URL url, String contextPath) throws Exception {
            this.jenkins = Jenkins.get();
            this.url = url;
            this.contextPath = contextPath;
            if (jenkins.isUsageStatisticsCollected()) {
                jenkins.setNoUsageStatistics(
                        true); // cannot use JenkinsRule._configureJenkinsForTest earlier because it tries to save
                // config before loaded
            }
            if (JenkinsLocationConfiguration.get().getUrl() == null) {
                JenkinsLocationConfiguration.get().setUrl(url.toExternalForm());
            }
            testDescription = Description.createSuiteDescription(System.getProperty("RealJenkinsFixture.description"));
            env = new TestEnvironment(this.testDescription);
            env.pin();
        }

        @Override
        public URL getURL() throws IOException {
            return url;
        }

        @Override
        public void close() throws Exception {
            env.dispose();
        }
    }

    // Copied from hudson.remoting
    public static final class ProxyException extends IOException {
        ProxyException(Throwable cause) {
            super(cause.toString(), cause);
            setStackTrace(cause.getStackTrace());
            if (cause.getCause() != null) {
                initCause(new ProxyException(cause.getCause()));
            }
            for (Throwable suppressed : cause.getSuppressed()) {
                addSuppressed(new ProxyException(suppressed));
            }
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }

    private static class StepsToStep2 implements Step2<Serializable> {
        private final Step[] steps;

        StepsToStep2(Step... steps) {
            this.steps = steps;
        }

        @Override
        public Serializable run(JenkinsRule r) throws Throwable {
            for (Step step : steps) {
                step.run(r);
            }
            return null;
        }
    }

    public static class JenkinsStartupException extends IOException {
        public JenkinsStartupException(String message) {
            super(message);
        }
    }

    public static class StepException extends Exception {
        StepException(Throwable cause, @CheckForNull String name) {
            super(
                    name != null
                            ? "Remote step in " + name + " threw an exception: " + cause
                            : "Remote step threw an exception: " + cause,
                    cause);
        }
    }

    private static class InputPayload implements Serializable {
        private final String token;
        private final Step2<?> step;
        private final URL url;
        private final String contextPath;

        InputPayload(String token, Step2<?> step, URL url) {
            this.token = token;
            this.step = step;
            this.url = url;
            this.contextPath = url.getPath().replaceAll("/$", "");
        }
    }

    private static class OutputPayload implements Serializable {
        private final Object result;
        private final ProxyException error;
        private final String assumptionFailure;

        OutputPayload(Object result, Throwable error) {
            this.result = result;
            // TODO use raw error if it seems safe enough
            this.error = error != null ? new ProxyException(error) : null;
            assumptionFailure = error instanceof TestAbortedException || error instanceof AssumptionViolatedException
                    ? error.getMessage()
                    : null;
        }
    }

    /**
     * Alternative to {@link #addPlugins} or {@link TestExtension} that lets you build a test-only plugin on the fly.
     * ({@link ExtensionList#add(Object)} can also be used for certain cases, but not if you need to define new types.)
     */
    public static final class SyntheticPlugin {
        private final String pkg;
        private String shortName;
        private String version = "1-SNAPSHOT";
        private final Map<String, String> headers = new HashMap<>();

        /**
         * Creates a new synthetic plugin builder.
         *
         * @param exampleClass an example of a class from the Java package containing any classes and resources you want included
         * @see RealJenkinsFixture#addSyntheticPlugin
         * @see RealJenkinsFixture#createSyntheticPlugin
         */
        public SyntheticPlugin(Class<?> exampleClass) {
            this(exampleClass.getPackage());
        }

        /**
         * Creates a new synthetic plugin builder.
         *
         * @param pkg the Java package containing any classes and resources you want included
         * @see RealJenkinsFixture#addSyntheticPlugin
         * @see RealJenkinsFixture#createSyntheticPlugin
         */
        public SyntheticPlugin(Package pkg) {
            this(pkg.getName());
        }

        /**
         * Creates a new synthetic plugin builder.
         *
         * @param pkg the name of a Java package containing any classes and resources you want included
         * @see RealJenkinsFixture#addSyntheticPlugin
         * @see RealJenkinsFixture#createSyntheticPlugin
         */
        public SyntheticPlugin(String pkg) {
            this.pkg = pkg;
            shortName = "synthetic-" + this.pkg.replace('.', '-');
        }

        /**
         * Plugin identifier ({@code Short-Name} manifest header).
         * Defaults to being calculated from the package name,
         * replacing {@code .} with {@code -} and prefixed by {@code synthetic-}.
         */
        public SyntheticPlugin shortName(String shortName) {
            this.shortName = shortName;
            return this;
        }

        /**
         * Plugin version string ({@code Plugin-Version} manifest header).
         * Defaults to an arbitrary snapshot version.
         */
        public SyntheticPlugin version(String version) {
            this.version = version;
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
            headers.put(key, value);
            return this;
        }

        void writeTo(File jpi, String defaultJenkinsVersion) throws IOException, URISyntaxException {
            var mani = new Manifest();
            var attr = mani.getMainAttributes();
            attr.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attr.putValue("Short-Name", shortName);
            attr.putValue("Plugin-Version", version);
            attr.putValue("Jenkins-Version", defaultJenkinsVersion);
            for (var entry : headers.entrySet()) {
                attr.putValue(entry.getKey(), entry.getValue());
            }
            var jar = new ByteArrayOutputStream();
            try (var jos = new JarOutputStream(jar, mani)) {
                String pkgSlash = pkg.replace('.', '/');
                URL mainU = RealJenkinsFixture.class.getClassLoader().getResource(pkgSlash);
                if (mainU == null) {
                    throw new IOException("Cannot find " + pkgSlash + " in classpath");
                }
                Path main = Path.of(mainU.toURI());
                if (!Files.isDirectory(main)) {
                    throw new IOException(main + " does not exist");
                }
                Path metaInf =
                        Path.of(URI.create(mainU.toString().replaceFirst("\\Q" + pkgSlash + "\\E/?$", "META-INF")));
                if (Files.isDirectory(metaInf)) {
                    zip(jos, metaInf, "META-INF/", pkg);
                }
                zip(jos, main, pkgSlash + "/", null);
            }
            try (var os = new FileOutputStream(jpi);
                    var jos = new JarOutputStream(os, mani)) {
                jos.putNextEntry(new JarEntry("WEB-INF/lib/" + shortName + ".jar"));
                jos.write(jar.toByteArray());
            }
            LOGGER.info(() -> "Generated " + jpi);
        }

        private void zip(ZipOutputStream zos, Path dir, String prefix, @CheckForNull String filter) throws IOException {
            try (Stream<Path> stream = Files.list(dir)) {
                Iterable<Path> iterable = stream::iterator;
                for (Path child : iterable) {
                    Path nameP = child.getFileName();
                    assert nameP != null;
                    String name = nameP.toString();
                    if (Files.isDirectory(child)) {
                        zip(zos, child, prefix + name + "/", filter);
                    } else {
                        if (filter != null) {
                            // Deliberately not using UTF-8 since the file could be binary.
                            // If the package name happened to be non-ASCII, 🤷 this could be improved.
                            if (!Files.readString(child, StandardCharsets.ISO_8859_1)
                                    .contains(filter)) {
                                LOGGER.info(() -> "Skipping " + child + " since it makes no mention of " + filter);
                                continue;
                            }
                        }
                        LOGGER.info(() -> "Packing " + child);
                        zos.putNextEntry(new ZipEntry(prefix + name));
                        Files.copy(child, zos);
                    }
                }
            }
        }
    }
}
