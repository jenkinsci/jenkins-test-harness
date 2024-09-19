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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.NamingThreadFactory;
import hudson.util.StreamCopyThread;
import java.io.BufferedReader;
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
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.security.MessageDigest;
import java.util.ArrayList;
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
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.jenkins.test.fips.FIPSTestBundleProvider;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.util.Timer;
import org.apache.commons.io.FileUtils;
import org.junit.AssumptionViolatedException;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

/**
 * Like {@link JenkinsSessionRule} but running Jenkins in a more realistic environment.
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
 * <p>Known limitations:
 * <ul>
 * <li>Execution is a bit slower due to the overhead of launching a new JVM; and class loading overhead cannot be shared between test cases. More memory is needed.
 * <li>Remote thunks must be serializable. If they need data from the test JVM, you will need to create a {@code static} nested class to package that.
 * <li>{@code static} state cannot be shared between the top-level test code and test bodies (though the compiler will not catch this mistake).
 * <li>When using a snapshot dep on Jenkins core, you must build {@code jenkins.war} to test core changes (there is no “compile-on-save” support for this).
 * <li>{@link TestExtension} is not available.
 * <li>{@link LoggerRule} is not available, however additional loggers can be configured via {@link #withLogger(Class, Level)}}.
 * <li>{@link BuildWatcher} is not available, but you can use {@link TailLog} instead.
 * </ul>
 * <p>Systems not yet tested:
 * <ul>
 * <li>Possibly {@link Timeout} can be used.
 * <li>Possibly {@link ExtensionList#add(Object)} can be used as an alternative to {@link TestExtension}.
 * </ul>
 */
public final class RealJenkinsRule implements TestRule {

    private static final Logger LOGGER = Logger.getLogger(RealJenkinsRule.class.getName());

    private static final String REAL_JENKINS_RULE_LOGGING = "RealJenkinsRule.logging.";

    private Description description;

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

    private final Set<String> skippedPlugins = new TreeSet<>();

    private final List<String> javaOptions = new ArrayList<>();

    private final List<String> jenkinsOptions = new ArrayList<>();

    private final Map<String, String> extraEnv = new TreeMap<>();

    private int timeout = Integer.getInteger("jenkins.test.timeout", new DisableOnDebug(null).isDebugging() ? 0 : 600);

    private String host = "localhost";

    Process proc;

    private Path portFile;

    private Map<String, Level> loggers = new HashMap<>();

    private int debugPort = 0;
    private boolean debugServer = true;
    private boolean debugSuspend;

    private boolean prepareHomeLazily;
    private boolean provisioned;
    private final List<File> bootClasspathFiles = new ArrayList<>();

    // TODO may need to be relaxed for Gradle-based plugins
    private static final Pattern SNAPSHOT_INDEX_JELLY = Pattern.compile("(file:/.+/target)/classes/index.jelly");

    private final PrefixedOutputStream.Builder prefixedOutputStreamBuilder = PrefixedOutputStream.builder();

    public RealJenkinsRule() {
        home = new AtomicReference<>();
    }

    /**
     * Links this rule to another, with {@link #getHome} to be initialized by whichever copy starts first.
     * Also copies configuration related to the setup of that directory:
     * {@link #includeTestClasspathPlugins(boolean)}, {@link #addPlugins}, and {@link #omitPlugins}.
     * Other configuration such as {@link #javaOptions(String...)} may be applied to both, but that is your choice.
     */
    public RealJenkinsRule(RealJenkinsRule source) {
        this.home = source.home;
        this.includeTestClasspathPlugins = source.includeTestClasspathPlugins;
        this.extraPlugins.addAll(source.extraPlugins);
        this.skippedPlugins.addAll(source.skippedPlugins);
    }

    /**
     * Add some plugins to the test classpath.
     *
     * @param plugins Filenames of the plugins to install. These are expected to be absolute test classpath resources,
     *     such as {@code plugins/workflow-job.hpi} for example.
     *     <p>Committing that file to SCM (say, {@code src/test/resources/sample.jpi}) is
     *     reasonable for small fake plugins built for this purpose and exercising some bit of code.
     *     If you wish to test with larger archives of real plugins, this is possible for example by
     *     binding {@code dependency:copy} to the {@code process-test-resources} phase.
     *     <p>In most cases you do not need this method. Simply add whatever plugins you are
     *     interested in testing against to your POM in {@code test} scope. These, and their
     *     transitive dependencies, will be loaded in all {@link RealJenkinsRule} tests. This method
     *     is useful if only a particular test may load the tested plugin, or if the tested plugin
     *     is not available in a repository for use as a test dependency.
     */
    public RealJenkinsRule addPlugins(String... plugins) {
        extraPlugins.addAll(List.of(plugins));
        return this;
    }

    /**
     * Omit some plugins in the test classpath.
     * @param plugins one or more code names, like {@code token-macro}
     */
    public RealJenkinsRule omitPlugins(String... plugins) {
        skippedPlugins.addAll(List.of(plugins));
        return this;
    }

    /**
     * Add some JVM startup options.
     * @param options one or more options, like {@code -Dorg.jenkinsci.Something.FLAG=true}
     */
    public RealJenkinsRule javaOptions(String... options) {
        javaOptions.addAll(List.of(options));
        return this;
    }

    /**
     * Add some Jenkins (including Winstone) startup options.
     * You probably meant to use {@link #javaOptions(String...)}.
     * @param options one or more options, like {@code --webroot=/tmp/war --pluginroot=/tmp/plugins}
     */
    public RealJenkinsRule jenkinsOptions(String... options) {
        jenkinsOptions.addAll(List.of(options));
        return this;
    }

    /**
     * Set an extra environment variable.
     * @param value null to cancel a previously set variable
     */
    public RealJenkinsRule extraEnv(String key, String value) {
        extraEnv.put(key, value);
        return this;
    }

    /**
     * Adjusts the test timeout.
     * The timer starts when {@link #startJenkins} completes and {@link #runRemotely} is ready.
     * The default is currently set to 600 (10m).
     * @param timeout number of seconds before exiting, or zero to disable
     */
    public RealJenkinsRule withTimeout(int timeout) {
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
     */
    public RealJenkinsRule withHost(String host) {
        this.host = host;
        return this;
    }

    /**
     * Sets a custom WAR file to be used by the rule instead of the one in the path or {@code war/target/jenkins.war} in case of core.
     */
    public RealJenkinsRule withWar(File war) {
        this.war = war;
        return this;
    }

    /**
     * Allows to specify a java home, defaults to JAVA_HOME if not used
     */
    public RealJenkinsRule withJavaHome(String JavaHome) {
        this.javaHome = JavaHome;
        return this;
    }

    public RealJenkinsRule withLogger(Class<?> clazz, Level level) {
        return withLogger(clazz.getName(), level);
    }

    public RealJenkinsRule withPackageLogger(Class<?> clazz, Level level) {
        return withLogger(clazz.getPackageName(), level);
    }

    public RealJenkinsRule withLogger(String logger, Level level) {
        this.loggers.put(logger, level);
        return this;
    }

    /**
     * Sets a name for this instance, which will be prefixed to log messages to simplify debugging.
     */
    public RealJenkinsRule withName(String name) {
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
    public RealJenkinsRule withColor(PrefixedOutputStream.AnsiColor color) {
        prefixedOutputStreamBuilder.withColor(color);
        return this;
    }

    /**
     * Provides a custom fixed port instead of a random one.
     * @param port a custom port to use instead of a random one.
     */
    public RealJenkinsRule withPort(int port) {
        this.port = port;
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
        if (debugPort < 0) {
            throw new IllegalArgumentException("debugPort must be positive");
        }
        if (!(debugPort < 65536)) {
            throw new IllegalArgumentException("debugPort must be a valid TCP port (< 65536)");
        }
        this.debugPort = debugPort;
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
        this.debugServer = debugServer;
        return this;
    }

    /**
     * Whether to suspend the controller VM on startup until debugger is connected. Defaults to false.
     * @param debugSuspend true to suspend the controller VM on startup until debugger is connected.
     */
    public RealJenkinsRule withDebugSuspend(boolean debugSuspend) {
        this.debugSuspend = debugSuspend;
        return this;
    }

    /**
     * The intended use case for this is to use the plugins bundled into the war {@link RealJenkinsRule#withWar(File)}
     * instead of the plugins in the pom. A typical scenario for this feature is a test which does not live inside a
     * plugin's src/test/java
     * @param includeTestClasspathPlugins false if plugins from pom should not be used (default true)
     */
    public RealJenkinsRule includeTestClasspathPlugins(boolean includeTestClasspathPlugins) {
        this.includeTestClasspathPlugins = includeTestClasspathPlugins;
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
        this.prepareHomeLazily = prepareHomeLazily;
        return this;
    }

    /**
     * Use {@link #withFIPSEnabled(FIPSTestBundleProvider)}  with default value of {@link FIPSTestBundleProvider#get()}
     */
    public RealJenkinsRule withFIPSEnabled() {
        return withFIPSEnabled(FIPSTestBundleProvider.get());
    }

    /**
     +
     * @param fipsTestBundleProvider the {@link FIPSTestBundleProvider} to use for testing
     */
    public RealJenkinsRule withFIPSEnabled(FIPSTestBundleProvider fipsTestBundleProvider) {
        Objects.requireNonNull(fipsTestBundleProvider, "fipsTestBundleProvider must not be null");
        try {
            return withBootClasspath(fipsTestBundleProvider.getBootClasspathFiles().toArray(new File[0]))
                    .javaOptions(fipsTestBundleProvider.getJavaOptions().toArray(new String[0]));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @param files add some {@link File} to bootclasspath
     */
    public RealJenkinsRule withBootClasspath(File...files) {
        this.bootClasspathFiles.addAll(List.of(files));
        return this;
    }

    public static List<String> getJacocoAgentOptions() {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMxBean.getInputArguments();
        return arguments.stream()
                .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("jacoco"))
                .collect(Collectors.toList());
    }

    @Override public Statement apply(final Statement base, Description description) {
        this.description = description;
        return new Statement() {
            @Override public void evaluate() throws Throwable {
                System.out.println("=== Starting " + description);
                try {
                    jenkinsOptions("--webroot=" + createTempDirectory("webroot"), "--pluginroot=" + createTempDirectory("pluginroot"));
                    if (war == null) {
                        war = findJenkinsWar();
                    }
                    if (home.get() != null) {
                        try {
                            base.evaluate();
                        } finally {
                            stopJenkins();
                        }
                        return;
                    }
                    try {
                        home.set(tmp.allocate());
                        if (!prepareHomeLazily) {
                            provision();
                        }
                        base.evaluate();
                    } finally {
                        stopJenkins();
                    }
                } finally {
                    try {
                        tmp.dispose();
                    } catch (Exception x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
            }

        };
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
        LocalData localData = description.getAnnotation(LocalData.class);
        if (localData != null) {
            new HudsonHomeLoader.Local(description.getTestClass().getMethod(description.getMethodName()), localData.value()).copy(getHome());
        }

        File plugins = new File(getHome(), "plugins");
        Files.createDirectories(plugins.toPath());
        try (JarFile jf = new JarFile(war)) {
            // set the version to the version of jenkins used for testing to avoid dragging in detached plugins
            String targetJenkinsVersion = jf.getManifest().getMainAttributes().getValue("Jenkins-Version");
            PluginUtils.createRealJenkinsRulePlugin(plugins, targetJenkinsVersion);
        }

        if (includeTestClasspathPlugins) {
            // Adapted from UnitTestSupportingPluginManager & JenkinsRule.recipeLoadCurrentPlugin:
            Set<String> snapshotPlugins = new TreeSet<>();
            Enumeration<URL> indexJellies = RealJenkinsRule.class.getClassLoader().getResources("index.jelly");
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
                        // Not totally realistic, but test phase is run before package phase. TODO can we add an option to run in integration-test phase?
                        Files.copy(snapshotManifest, plugins.toPath().resolve(shortName + ".jpl"));
                        snapshotPlugins.add(shortName);
                    } else {
                        System.out.println("Warning: found " + indexJelly + " but did not find corresponding ../test-classes/the.[hj]pl");
                    }
                } else {
                    // Do not warn about the common case of jar:file:/**/.m2/repository/**/*.jar!/index.jelly
                }
            }
            URL index = RealJenkinsRule.class.getResource("/test-dependencies/index");
            if (index != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(index.openStream(), StandardCharsets.UTF_8))) {
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
                                        "You are probably trying to load plugins from within a jarfile (not possible). If" +
                                                " you are running this in your IDE and see this message, it is likely" +
                                                " that you have a clean target directory. Try running 'mvn test-compile' " +
                                                "from the command line (once only), which will copy the required plugins " +
                                                "into target/test-classes/test-dependencies - then retry your test", x);
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
            URL url = RealJenkinsRule.class.getClassLoader().getResource(extraPlugin);
            String name;
            try (InputStream is = url.openStream(); JarInputStream jis = new JarInputStream(is)) {
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
        System.out.println("Will load plugins: " + Stream.of(plugins.list()).filter(n -> n.matches(".+[.][hj]p[il]")).sorted().collect(Collectors.joining(" ")));
    }

    /**
     * Deletes {@code JENKINS_HOME}.
     *
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
     * One step to run.
     * <p>Since this thunk will be sent to a different JVM, it must be serializable.
     * The test class will certainly not be serializable, so you cannot use an anonymous inner class.
     * The friendliest idiom is a static method reference:
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
     */
    public URL getUrl() throws MalformedURLException {
        if (port == 0) {
            throw new IllegalStateException("This method must be called after calling #startJenkins.");
        }
        return new URL("http", host, port, "/jenkins/");
    }

    private URL endpoint(String method) throws MalformedURLException {
        return new URL(getUrl(), "RealJenkinsRule/" + method + "?token=" + token);
    }

    /**
     * Obtains the Jenkins home directory.
     * Normally it will suffice to use {@link LocalData} to populate files.
     */
    public File getHome() {
        return home.get();
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

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "irrelevant")
    public void startJenkins() throws Throwable {
        if (proc != null) {
            throw new IllegalStateException("Jenkins is (supposedly) already running");
        }
        if (prepareHomeLazily && !provisioned) {
            provision();
        }
        var metadata = createTempDirectory("RealJenkinsRule");
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
                "-DRealJenkinsRule.classpath=" + cpFile,
                "-DRealJenkinsRule.location=" + RealJenkinsRule.class.getProtectionDomain().getCodeSource().getLocation(),
                "-DRealJenkinsRule.description=" + description,
                "-DRealJenkinsRule.token=" + token));
        argv.addAll(getJacocoAgentOptions());
        for (Map.Entry<String, Level> e : loggers.entrySet()) {
            argv.add("-D" + REAL_JENKINS_RULE_LOGGING + e.getKey() + "=" + e.getValue().getName());
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
        if(!bootClasspathFiles.isEmpty()) {
            String fileList = bootClasspathFiles.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
            argv.add("-Xbootclasspath/a:" + fileList);

        }
        argv.addAll(javaOptions);

        argv.addAll(List.of(
                "-jar", war.getAbsolutePath(),
                "--enable-future-java",
                "--httpPort=" + port, // initially port=0. On subsequent runs, the port is set to the port used allocated randomly on the first run.
                "--httpListenAddress=" + httpListenAddress,
                "--prefix=/jenkins"));
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
        // TODO escape spaces like Launcher.printCommandLine, or LabelAtom.escape (beware that QuotedStringTokenizer.quote(String) Javadoc is untrue):
        System.out.println(env.entrySet().stream().map(Map.Entry::toString).collect(Collectors.joining(" ")) + " " + String.join(" ", argv));
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.environment().putAll(env);
        // TODO options to set Winstone options, etc.
        // TODO pluggable launcher interface to support a Dockerized Jenkins JVM
        pb.redirectErrorStream(true);
        proc = pb.start();
        new StreamCopyThread(description.toString(), proc.getInputStream(), prefixedOutputStreamBuilder.build(System.out)).start();
        int tries = 0;
        while (true) {
            if (!proc.isAlive()) {
                int exitValue = proc.exitValue();
                proc = null;
                throw new IOException("Jenkins process terminated prematurely with exit code " + exitValue);
            }
            if (port == 0 && portFile != null && Files.isRegularFile(portFile)) {
                port = readPort(portFile);
            }
            if (port != 0) {
                try {
                    URL status = endpoint("status");
                    HttpURLConnection conn = (HttpURLConnection) status.openConnection();

                    String checkResult = checkResult(conn);
                    if (checkResult == null) {
                        System.out.println((getName() != null ? getName() : "Jenkins") + " is running at " + getUrl());
                        break;
                    }else {
                        throw new IOException("Response code " + conn.getResponseCode() + " for " + status + ": " + checkResult +
                                                      " " + conn.getHeaderFields());
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
            Timer.get().schedule(() -> {
                if (proc != null) {
                    LOGGER.warning("Test timeout expired, stopping steps…");
                    try {
                        endpoint("timeout").openStream().close();
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
            }, timeout, TimeUnit.SECONDS);
        }
    }

    private static int readPort(Path portFile) throws IOException {
        String s = Files.readString(portFile, StandardCharsets.UTF_8);

        // Work around to non-atomic write of port value in Winstone releases prior to 6.1.
        // TODO When Winstone 6.2 has been widely adopted, this can be deleted.
        if (s.isEmpty()) {
            LOGGER.warning(() -> String.format("PortFile: %s exists, but value is still not written", portFile));
            return 0;
        }

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
    public void stopJenkins() throws Throwable {
        if (proc != null) {
            Process _proc = proc;
            proc = null;
            if (_proc.isAlive()) {
                try {
                    endpoint("exit").openStream().close();
                } catch (SocketException e) {
                    System.err.println("Unable to connect to the Jenkins process to stop it: " + e);
                }
            } else {
                System.err.println("Jenkins process was already terminated.");
            }
            if (!_proc.waitFor(60, TimeUnit.SECONDS) ) {
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
     * Runs one or more steps on the remote system.
     * (Compared to multiple calls, passing a series of steps is slightly more efficient
     * as only one network call is made.)
     */
    public void runRemotely(Step... steps) throws Throwable {
        runRemotely(new StepsToStep2(steps));
    }

    @SuppressWarnings("unchecked")
    public <T extends Serializable> T runRemotely(Step2<T> s) throws Throwable {
        HttpURLConnection conn = (HttpURLConnection) endpoint("step").openConnection();
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setDoOutput(true);

        Init2.writeSer(conn.getOutputStream(), new InputPayload(token, s, getUrl()));
        try {
            OutputPayload result = (OutputPayload) Init2.readSer(conn.getInputStream(), null);
            if (result.assumptionFailure != null) {
                throw new AssumptionViolatedException(result.assumptionFailure, result.error);
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
        @Override public void run(JenkinsRule r) throws Throwable {
            delegate.run(r, arg1);
        }
    }

    @FunctionalInterface
    public interface StepWithTwoArgs<A1 extends Serializable, A2 extends Serializable> extends Serializable {
       void run(JenkinsRule r, A1 arg1, A2 arg2) throws Throwable;
    }
    public <A1 extends Serializable, A2 extends Serializable> void runRemotely(StepWithTwoArgs<A1, A2> s, A1 arg1, A2 arg2) throws Throwable {
        runRemotely(new StepWithTwoArgsWrapper<>(s, arg1, arg2));
    }
    private static final class StepWithTwoArgsWrapper<A1 extends Serializable, A2 extends Serializable> implements Step {
        private final StepWithTwoArgs<A1, A2> delegate;
        private final A1 arg1;
        private final A2 arg2;
        StepWithTwoArgsWrapper(StepWithTwoArgs<A1, A2> delegate, A1 arg1, A2 arg2) {
            this.delegate = delegate;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }
        @Override public void run(JenkinsRule r) throws Throwable {
            delegate.run(r, arg1, arg2);
        }
    }

    @FunctionalInterface
    public interface StepWithThreeArgs<A1 extends Serializable, A2 extends Serializable, A3 extends Serializable> extends Serializable {
       void run(JenkinsRule r, A1 arg1, A2 arg2, A3 arg3) throws Throwable;
    }
    public <A1 extends Serializable, A2 extends Serializable, A3 extends Serializable> void runRemotely(StepWithThreeArgs<A1, A2, A3> s, A1 arg1, A2 arg2, A3 arg3) throws Throwable {
        runRemotely(new StepWithThreeArgsWrapper<>(s, arg1, arg2, arg3));
    }
    private static final class StepWithThreeArgsWrapper<A1 extends Serializable, A2 extends Serializable, A3 extends Serializable> implements Step {
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
        @Override public void run(JenkinsRule r) throws Throwable {
            delegate.run(r, arg1, arg2, arg3);
        }
    }

    @FunctionalInterface
    public interface StepWithFourArgs<A1 extends Serializable, A2 extends Serializable, A3 extends Serializable, A4 extends Serializable> extends Serializable {
       void run(JenkinsRule r, A1 arg1, A2 arg2, A3 arg3, A4 arg4) throws Throwable;
    }
    public <A1 extends Serializable, A2 extends Serializable, A3 extends Serializable, A4 extends Serializable> void runRemotely(StepWithFourArgs<A1, A2, A3, A4> s, A1 arg1, A2 arg2, A3 arg3, A4 arg4) throws Throwable {
        runRemotely(new StepWithFourArgsWrapper<>(s, arg1, arg2, arg3, arg4));
    }
    private static final class StepWithFourArgsWrapper<A1 extends Serializable, A2 extends Serializable, A3 extends Serializable, A4 extends Serializable> implements Step {
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
        @Override public void run(JenkinsRule r) throws Throwable {
            delegate.run(r, arg1, arg2, arg3, arg4);
        }
    }

    @FunctionalInterface
    public interface StepWithReturnAndOneArg<R extends Serializable, A1 extends Serializable> extends Serializable {
        R run(JenkinsRule r, A1 arg1) throws Throwable;
    }
    public <R extends Serializable, A1 extends Serializable> R runRemotely(StepWithReturnAndOneArg<R, A1> s, A1 arg1) throws Throwable {
        return runRemotely(new StepWithReturnAndOneArgWrapper<>(s, arg1));
    }
    private static final class StepWithReturnAndOneArgWrapper<R extends Serializable, A1 extends Serializable> implements Step2<R> {
        private final StepWithReturnAndOneArg<R, A1> delegate;
        private final A1 arg1;
        StepWithReturnAndOneArgWrapper(StepWithReturnAndOneArg<R, A1> delegate, A1 arg1) {
            this.delegate = delegate;
            this.arg1 = arg1;
        }
        @Override public R run(JenkinsRule r) throws Throwable {
            return delegate.run(r, arg1);
        }
    }

    @FunctionalInterface
    public interface StepWithReturnAndTwoArgs<R extends Serializable, A1 extends Serializable, A2 extends Serializable> extends Serializable {
        R run(JenkinsRule r, A1 arg1, A2 arg2) throws Throwable;
    }
    public <R extends Serializable, A1 extends Serializable, A2 extends Serializable> R runRemotely(StepWithReturnAndTwoArgs<R, A1, A2> s, A1 arg1, A2 arg2) throws Throwable {
        return runRemotely(new StepWithReturnAndTwoArgsWrapper<>(s, arg1, arg2));
    }
    private static final class StepWithReturnAndTwoArgsWrapper<R extends Serializable, A1 extends Serializable, A2 extends Serializable> implements Step2<R> {
        private final StepWithReturnAndTwoArgs<R, A1, A2> delegate;
        private final A1 arg1;
        private final A2 arg2;
        StepWithReturnAndTwoArgsWrapper(StepWithReturnAndTwoArgs<R, A1, A2> delegate, A1 arg1, A2 arg2) {
            this.delegate = delegate;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }
        @Override public R run(JenkinsRule r) throws Throwable {
            return delegate.run(r, arg1, arg2);
        }
    }

    @FunctionalInterface
    public interface StepWithReturnAndThreeArgs<R extends Serializable, A1 extends Serializable, A2 extends Serializable, A3 extends Serializable> extends Serializable {
        R run(JenkinsRule r, A1 arg1, A2 arg2, A3 arg3) throws Throwable;
    }
    public <R extends Serializable, A1 extends Serializable, A2 extends Serializable, A3 extends Serializable> R runRemotely(StepWithReturnAndThreeArgs<R, A1, A2, A3> s, A1 arg1, A2 arg2, A3 arg3) throws Throwable {
        return runRemotely(new StepWithReturnAndThreeArgsWrapper<>(s, arg1, arg2, arg3));
    }
    private static final class StepWithReturnAndThreeArgsWrapper<R extends Serializable, A1 extends Serializable, A2 extends Serializable, A3 extends Serializable> implements Step2<R> {
        private final StepWithReturnAndThreeArgs<R, A1, A2, A3> delegate;
        private final A1 arg1;
        private final A2 arg2;
        private final A3 arg3;
        StepWithReturnAndThreeArgsWrapper(StepWithReturnAndThreeArgs<R, A1, A2, A3> delegate, A1 arg1, A2 arg2, A3 arg3) {
            this.delegate = delegate;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
        }
        @Override public R run(JenkinsRule r) throws Throwable {
            return delegate.run(r, arg1, arg2, arg3);
        }
    }

    @FunctionalInterface
    public interface StepWithReturnAndFourArgs<R extends Serializable, A1 extends Serializable, A2 extends Serializable, A3 extends Serializable, A4 extends Serializable> extends Serializable {
        R run(JenkinsRule r, A1 arg1, A2 arg2, A3 arg3, A4 arg4) throws Throwable;
    }
    public <R extends Serializable, A1 extends Serializable, A2 extends Serializable, A3 extends Serializable, A4 extends Serializable> R runRemotely(StepWithReturnAndFourArgs<R, A1, A2, A3, A4> s, A1 arg1, A2 arg2, A3 arg3, A4 arg4) throws Throwable {
        return runRemotely(new StepWithReturnAndFourArgsWrapper<>(s, arg1, arg2, arg3, arg4));
    }
    private static final class StepWithReturnAndFourArgsWrapper<R extends Serializable, A1 extends Serializable, A2 extends Serializable, A3 extends Serializable, A4 extends Serializable> implements Step2<R> {
        private final StepWithReturnAndFourArgs<R, A1, A2, A3, A4> delegate;
        private final A1 arg1;
        private final A2 arg2;
        private final A3 arg3;
        private final A4 arg4;
        StepWithReturnAndFourArgsWrapper(StepWithReturnAndFourArgs<R, A1, A2, A3, A4> delegate, A1 arg1, A2 arg2, A3 arg3, A4 arg4) {
            this.delegate = delegate;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
            this.arg4 = arg4;
        }
        @Override public R run(JenkinsRule r) throws Throwable {
            return delegate.run(r, arg1, arg2, arg3, arg4);
        }
    }

    // Should not refer to any types outside the JRE.
    public static final class Init2 {

        public static void run(Object jenkins) throws Exception {
            Object pluginManager = jenkins.getClass().getField("pluginManager").get(jenkins);
            ClassLoader uberClassLoader = (ClassLoader) pluginManager.getClass().getField("uberClassLoader").get(pluginManager);
            ClassLoader tests = new URLClassLoader(Files.readAllLines(Paths.get(System.getProperty("RealJenkinsRule.classpath")), StandardCharsets.UTF_8).stream().map(Init2::pathToURL).toArray(URL[]::new), uberClassLoader);
            tests.loadClass("org.jvnet.hudson.test.RealJenkinsRule$Endpoint").getMethod("register").invoke(null);
        }

        private static URL pathToURL(String path) {
            try {
                return Paths.get(path).toUri().toURL();
            } catch (MalformedURLException x) {
                throw new IllegalArgumentException(x);
            }
        }

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

        static Object readSer(File f, ClassLoader loader) throws Exception {
            try (InputStream is = new FileInputStream(f)) {
                return readSer(is, loader);
            }
        }

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

        private Init2() {}

    }

    public static final class Endpoint implements UnprotectedRootAction {
        @SuppressWarnings("deprecation")
        public static void register() throws Exception {
            Jenkins j = Jenkins.get();
            configureLogging();
            j.getActions().add(new Endpoint());
            CrumbExclusion.all().add(new CrumbExclusion() {
                @Override public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
                    if (request.getPathInfo().startsWith("/RealJenkinsRule/")) {
                        chain.doFilter(request, response);
                        return true;
                    }
                    return false;
                }
            });
            JenkinsRule._configureUpdateCenter(j);
            System.err.println("RealJenkinsRule ready");
            if (!new DisableOnDebug(null).isDebugging()) {
                Timer.get().scheduleAtFixedRate(JenkinsRule::dumpThreads, 2, 2, TimeUnit.MINUTES);
            }
        }

        private static Set<Logger> loggers = new HashSet<>();

        private static void configureLogging() {
            Level minLevel = Level.INFO;
            for (String propertyName : System.getProperties().stringPropertyNames()) {
                if (propertyName.startsWith(REAL_JENKINS_RULE_LOGGING)) {
                    String loggerName = propertyName.substring(REAL_JENKINS_RULE_LOGGING.length());
                    Logger logger = Logger.getLogger(loggerName);
                    Level level = Level.parse(System.getProperty(propertyName));
                    if (level.intValue() < minLevel.intValue()) {
                        minLevel = level;
                    }
                    logger.setLevel(level);
                    loggers.add(logger); // Keep a ref around, otherwise it is garbage collected and we lose configuration
                }
            }
            // Increase ConsoleHandler level to the finest level we want to log.
            if (!loggers.isEmpty()) {
                for (Handler h : Logger.getLogger("").getHandlers()) {
                    if (h instanceof ConsoleHandler) {
                        h.setLevel(minLevel);
                    }
                }
            }
        }

        @Override public String getUrlName() {
            return "RealJenkinsRule";
        }
        @Override public String getIconFileName() {
            return null;
        }
        @Override public String getDisplayName() {
            return null;
        }
        private final byte[] actualToken = System.getProperty("RealJenkinsRule.token").getBytes(StandardCharsets.US_ASCII);
        private void checkToken(String token) {
            if (!MessageDigest.isEqual(actualToken, token.getBytes(StandardCharsets.US_ASCII))) {
                throw HttpResponses.forbidden();
            }
        }
        public void doStatus(@QueryParameter String token) {
            System.err.println("Checking status");
            checkToken(token);
        }
        /**
         * Used to run test methods on a separate thread so that code that uses {@link Stapler#getCurrentRequest}
         * does not inadvertently interact with the request for {@link #doStep} itself.
         */
        private static final ExecutorService STEP_RUNNER = Executors.newSingleThreadExecutor(
                new NamingThreadFactory(Executors.defaultThreadFactory(), RealJenkinsRule.class.getName() + ".STEP_RUNNER"));
        @POST
        public void doStep(StaplerRequest req, StaplerResponse rsp) throws Throwable {
            InputPayload input = (InputPayload) Init2.readSer(req.getInputStream(), Endpoint.class.getClassLoader());
            checkToken(input.token);
            Step2<?> s = input.step;
            URL url = input.url;

            Throwable err = null;
            Object object = null;
            try {
                object = STEP_RUNNER.submit(() -> {
                    try (CustomJenkinsRule rule = new CustomJenkinsRule(url); ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                        return s.run(rule);
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                }).get();
            } catch (ExecutionException e) {
                // Unwrap once for ExecutionException and once for RuntimeException:
                err = e.getCause().getCause();
            } catch (CancellationException | InterruptedException e) {
                err = e;
            }
            Init2.writeSer(rsp.getOutputStream(), new OutputPayload(object, err));
        }
        public HttpResponse doExit(@QueryParameter String token) throws IOException {
            checkToken(token);
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                return Jenkins.get().doSafeExit((StaplerRequest) null);
            }
        }
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

        public CustomJenkinsRule(URL url) throws Exception {
            this.jenkins = Jenkins.get();
            this.url = url;
            if (jenkins.isUsageStatisticsCollected()) {
                jenkins.setNoUsageStatistics(true); // cannot use JenkinsRule._configureJenkinsForTest earlier because it tries to save config before loaded
            }
            if (JenkinsLocationConfiguration.get().getUrl() == null) {
                JenkinsLocationConfiguration.get().setUrl(url.toExternalForm());
            }
            testDescription = Description.createSuiteDescription(System.getProperty("RealJenkinsRule.description"));
            env = new TestEnvironment(this.testDescription);
            env.pin();
        }

        @Override public URL getURL() throws IOException {
            return url;
        }

        @Override public void close() throws Exception {
            env.dispose();
        }

    }

    // Copied from hudson.remoting
    public static final class ProxyException extends IOException {
        ProxyException(Throwable cause) {
            super(cause.toString());
            setStackTrace(cause.getStackTrace());
            if (cause.getCause() != null) {
                initCause(new ProxyException(cause.getCause()));
            }
            for (Throwable suppressed : cause.getSuppressed()) {
                addSuppressed(new ProxyException(suppressed));
            }
        }
        @Override public String toString() {
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
            super(name != null ? "Remote step in " + name + " threw an exception: " + cause : "Remote step threw an exception: " + cause, cause);
        }
    }

    private static class InputPayload implements Serializable {
        private final String token;
        private final Step2<?> step;
        private final URL url;

        InputPayload(String token, Step2<?> step, URL url) {
            this.token = token;
            this.step = step;
            this.url = url;
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
            assumptionFailure = error instanceof AssumptionViolatedException ? error.getMessage() : null;
        }
    }
}
