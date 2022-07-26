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
import hudson.util.VersionNumber;
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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
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
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.util.Timer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.rules.DisableOnDebug;
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
 * <li>{@link LoggerRule} is not available.
 * <li>{@link BuildWatcher} is not available.
 * <li>There is not currently enough flexibility in how the controller is launched.
 * </ul>
 * <p>Systems not yet tested:
 * <ul>
 * <li>Possibly {@link Timeout} can be used.
 * <li>Possibly {@link ExtensionList#add(Object)} can be used as an alternative to {@link TestExtension}.
 * </ul>
 */
@SuppressFBWarnings(value = "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", justification = "TODO needs triage")
public final class RealJenkinsRule implements TestRule {

    private static final Logger LOGGER = Logger.getLogger(JenkinsSessionRule.class.getName());

    private static final VersionNumber v2339 = new VersionNumber("2.339");

    private Description description;

    private final TemporaryDirectoryAllocator tmp = new TemporaryDirectoryAllocator();

    /**
     * JENKINS_HOME dir, consistent across restarts.
     */
    private File home;

    /**
     * TCP/IP port that the server is listening on.
     * <p>
     * Before the first start, it will be 0. Once started, it is set to the actual port Jenkins is listening to.
     * <p>
     * Like the home directory, this will be consistent across restarts.
     */
    private int port;

    private File war;

    private boolean includeTestClasspathPlugins = true;

    private final String token = UUID.randomUUID().toString();

    private final Set<String> extraPlugins = new TreeSet<>();

    private final Set<String> skippedPlugins = new TreeSet<>();

    private final List<String> javaOptions = new ArrayList<>();

    private final Map<String, String> extraEnv = new TreeMap<>();

    private int timeout = Integer.getInteger("jenkins.test.timeout", new DisableOnDebug(null).isDebugging() ? 0 : 600);

    private String host = "localhost";

    private Process proc;

    private File portFile;

    // TODO may need to be relaxed for Gradle-based plugins
    private static final Pattern SNAPSHOT_INDEX_JELLY = Pattern.compile("(file:/.+/target)/classes/index.jelly");
    private transient boolean supportsPortFileName;

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
        extraPlugins.addAll(Arrays.asList(plugins));
        return this;
    }

    /**
     * Omit some plugins in the test classpath.
     * @param plugins one or more code names, like {@code token-macro}
     */
    public RealJenkinsRule omitPlugins(String... plugins) {
        skippedPlugins.addAll(Arrays.asList(plugins));
        return this;
    }

    /**
     * Add some JVM startup options.
     * @param options one or more options, like {@code -Dorg.jenkinsci.Something.FLAG=true}
     */
    public RealJenkinsRule javaOptions(String... options) {
        javaOptions.addAll(Arrays.asList(options));
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
     * such as {@code some-id.127.0.0.1.nip.io}.
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
     * The intended use case for this is to use the plugins bundled into the war {@link RealJenkinsRule#withWar(File)}
     * instead of the plugins in the pom. A typical scenario for this feature is a test which does not live inside a
     * plugin's src/test/java
     * @param includeTestClasspathPlugins false if plugins from pom should not be used (default true)
     */
    public RealJenkinsRule includeTestClasspathPlugins(boolean includeTestClasspathPlugins) {
        this.includeTestClasspathPlugins = includeTestClasspathPlugins;
        return this;
    }

    @Override public Statement apply(final Statement base, Description description) {
        this.description = description;
        return new Statement() {
            @Override public void evaluate() throws Throwable {
                System.out.println("=== Starting " + description);
                try {
                    home = tmp.allocate();
                    LocalData localData = description.getAnnotation(LocalData.class);
                    if (localData != null) {
                        new HudsonHomeLoader.Local(description.getTestClass().getMethod(description.getMethodName()), localData.value()).copy(home);
                    }
                    if (war == null) {
                        war = findJenkinsWar();
                    }
                    supportsPortFileName = supportsPortFileName(war.getAbsolutePath());
                    if (!supportsPortFileName) {
                        port = IOUtil.randomTcpPort();
                    }
                    File plugins = new File(home, "plugins");
                    plugins.mkdir();
                    FileUtils.copyURLToFile(RealJenkinsRule.class.getResource("RealJenkinsRuleInit.jpi"), new File(plugins, "RealJenkinsRuleInit.jpi"));

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
                    base.evaluate();
                } finally {
                    if (proc != null) {
                        stopJenkins();
                    }
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
     * One step to run.
     * <p>Since this thunk will be sent to a different JVM, it must be serializable.
     * The test class will certainly not be serializable, so you cannot use an anonymous inner class.
     * If your thunk requires no parameters from the test JVM, the friendliest idiom is a static method reference:
     * <pre>
     * &#64;Test public void stuff() throws Throwable {
     *     rr.then(YourTest::_stuff);
     * }
     * private static void _stuff(JenkinsRule r) throws Throwable {
     *     // as needed
     * }
     * </pre>
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
     * Run one Jenkins session, send a test thunk, and shut down.
     */
    public void then(Step s) throws Throwable {
        then(new StepToStep2(s));
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
     * Like {@link JenkinsRule#getURL} but does not require Jenkins to have been started yet.
     */
    public URL getUrl() throws MalformedURLException {
        return new URL("http://" + host + ":" + port + "/jenkins/");
    }

    private URL endpoint(String method) throws MalformedURLException {
        return new URL(getUrl(), "RealJenkinsRule/" + method + "?token=" + token);
    }

    /**
     * Obtains the Jenkins home directory.
     * Normally it will suffice to use {@link LocalData} to populate files.
     */
    public File getHome() {
        return home;
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

    public void startJenkins() throws Throwable {
        if (proc != null) {
            throw new IllegalStateException("Jenkins is (supposedly) already running");
        }
        String cp = System.getProperty("java.class.path");
        FileUtils.writeLines(new File(home, "RealJenkinsRule-cp.txt"), Arrays.asList(cp.split(File.pathSeparator)));
        List<String> argv = new ArrayList<>(Arrays.asList(
                new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
                "-ea",
                "-Dhudson.Main.development=true",
                "-DRealJenkinsRule.location=" + RealJenkinsRule.class.getProtectionDomain().getCodeSource().getLocation(),
                "-DRealJenkinsRule.description=" + description,
                "-DRealJenkinsRule.token=" + token));


        if (supportsPortFileName) {
            portFile = new File(home, "jenkins-port.txt");
            argv.add("-Dwinstone.portFileName=" + portFile);
        }
        if (new DisableOnDebug(null).isDebugging()) {
            argv.add("-agentlib:jdwp=transport=dt_socket,server=y");
        }
        argv.addAll(javaOptions);


        argv.addAll(Arrays.asList(
                "-jar", war.getAbsolutePath(),
                "--enable-future-java",
                "--httpPort=" + port, // initially port=0. On subsequent runs, the port is set to the port used allocated randomly on the first run.
                "--httpListenAddress=127.0.0.1",
                "--prefix=/jenkins"));
        ProcessBuilder pb = new ProcessBuilder(argv);
        System.out.println("Launching: " + pb.command());
        pb.environment().put("JENKINS_HOME", home.getAbsolutePath());
        for (Map.Entry<String, String> entry : extraEnv.entrySet()) {
            if (entry.getValue() != null) {
                pb.environment().put(entry.getKey(), entry.getValue());
            }
        }
        // TODO options to set Winstone options, etc.
        // TODO pluggable launcher interface to support a Dockerized Jenkins JVM
        // TODO if test JVM is running in a debugger, start Jenkins JVM in a debugger also
        proc = pb.start();
        // TODO prefix streams with per-test timestamps & port
        new StreamCopyThread(description.toString(), proc.getInputStream(), System.out).start();
        new StreamCopyThread(description.toString(), proc.getErrorStream(), System.err).start();
        int tries = 0;
        while (true) {
            if (port == 0 && portFile != null && portFile.exists()) {
                port = readPort(portFile);
            }
            if (port != 0) {

                // Currently this file is created when a runRemotely is executed (CustomJenkinsRule constructor)
                // This file is needed before for ConnectedMasters connection to OCs.
                File jcl = new File(getHome(),  "jenkins.model.JenkinsLocationConfiguration.xml");
                if(! jcl.exists()){
                    String value = "<?xml version='1.1' encoding='UTF-8'?>\n"
                                       + "<jenkins.model.JenkinsLocationConfiguration>\n"
                                       + "  <jenkinsUrl>"+getUrl()+"</jenkinsUrl>\n"
                                       + "</jenkins.model.JenkinsLocationConfiguration>%";
                    FileUtils.write(jcl, value, Charset.defaultCharset());
                }

                try {
                    URL status = endpoint("status");
                    HttpURLConnection conn = (HttpURLConnection) status.openConnection();

                    String checkResult = checkResult(conn);
                    if (checkResult == null) {
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
                    if (tries == /* 3m */ 1800) {
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
                    err = IOUtils.toString(is, StandardCharsets.UTF_8);
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
                    System.err.println("Test timeout expired, killing Jenkins process");
                    proc.destroyForcibly();
                    proc = null;
                }
            }, timeout, TimeUnit.SECONDS);
        }
    }

    private static boolean supportsPortFileName(String war) throws IOException {
        try (JarFile warFile = new JarFile(war)) {
            String jenkinsVersion = warFile.getManifest().getMainAttributes().getValue("Jenkins-Version");
            VersionNumber version = new VersionNumber(jenkinsVersion);
            return version.compareTo(v2339) >= 0;
        }
    }

    private static int readPort(File portFile) throws IOException {
        String s = FileUtils.readFileToString(portFile, StandardCharsets.UTF_8);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new AssertionError("Unable to parse port from " + s + ". Jenkins did not start.");
        }
    }

    public void stopJenkins() throws Throwable {
        endpoint("exit").openStream().close();
        if (!proc.waitFor(60, TimeUnit.SECONDS) ) {
            System.err.println("Jenkins failed to stop within 60 seconds, attempting to kill the Jenkins process");
            proc.destroyForcibly();
            throw new AssertionError("Jenkins failed to terminate within 60 seconds");
        }
        int exitValue = proc.exitValue();
        if (exitValue != 0) {
            throw new AssertionError("nonzero exit code: " + exitValue);
        }
        proc = null;
    }

    public void runRemotely(Step s) throws Throwable {
        runRemotely(new StepToStep2(s));
    }

    public <T extends Serializable> T runRemotely(Step2<T> s) throws Throwable {
        HttpURLConnection conn = (HttpURLConnection) endpoint("step").openConnection();
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setDoOutput(true);
        Init2.writeSer(conn.getOutputStream(), Arrays.asList(token, s, getUrl()));
        Throwable error;
        T object;
        try {
            List<Object> result = (List) Init2.readSer(conn.getInputStream(), null);
            object = (T) result.get(0);
            error = (Throwable) result.get(1);
        } catch (IOException e) {
            if (conn.getErrorStream() != null) {
                try {
                    String errorMessage = IOUtils.toString(conn.getErrorStream(), StandardCharsets.UTF_8);
                    e.addSuppressed(new IOException("Response body: " + errorMessage));
                } catch (IOException e2) {
                    e.addSuppressed(e2);
                }
            }
            throw e;
        }
        if (error != null) {
            throw error;
        }
        return object;
    }

    // Should not refer to any types outside the JRE.
    public static final class Init2 {

        public static void run(Object jenkins) throws Exception {
            Object pluginManager = jenkins.getClass().getField("pluginManager").get(jenkins);
            ClassLoader uberClassLoader = (ClassLoader) pluginManager.getClass().getField("uberClassLoader").get(pluginManager);
            ClassLoader tests = new URLClassLoader(Files.lines(Paths.get(System.getenv("JENKINS_HOME"), "RealJenkinsRule-cp.txt"), Charset.defaultCharset()).map(Init2::pathToURL).toArray(URL[]::new), uberClassLoader);
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
            List<?> tokenAndStep = (List<?>) Init2.readSer(req.getInputStream(), Endpoint.class.getClassLoader());
            checkToken((String) tokenAndStep.get(0));
            Step2<?> s = (Step2) tokenAndStep.get(1);
            URL url = (URL) tokenAndStep.get(2);

            Throwable err = null;
            Object object = null;
            try {
                object = STEP_RUNNER.submit(() -> {
                    try (CustomJenkinsRule rule = new CustomJenkinsRule(url); ACLContext ctx = ACL.as(ACL.SYSTEM)) {
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
            // TODO use raw err if it seems safe enough
            Init2.writeSer(rsp.getOutputStream(), Arrays.asList(object, err != null ? new ProxyException(err) : null));
        }
        public HttpResponse doExit(@QueryParameter String token) throws IOException {
            checkToken(token);
            try (ACLContext ctx = ACL.as(ACL.SYSTEM)) {
                return Jenkins.get().doSafeExit(null);
            }
        }
    }

    public static final class CustomJenkinsRule extends JenkinsRule implements AutoCloseable {
        private final URL url;

        public CustomJenkinsRule(URL url) throws Exception {
            this.jenkins = Jenkins.get();
            this.url = url;
            jenkins.setNoUsageStatistics(true); // cannot use JenkinsRule._configureJenkinsForTest earlier because it tries to save config before loaded
            JenkinsLocationConfiguration.get().setUrl(url.toExternalForm());
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

    private static class StepToStep2 implements Step2<Serializable> {
        private final Step s;

        public StepToStep2(Step s) {
            this.s = s;
        }

        @Override
        public Serializable run(JenkinsRule r) throws Throwable {
            s.run(r);
            return null;
        }
    }

    public static class JenkinsStartupException extends IOException {
        public JenkinsStartupException(String message) {
            super(message);
        }
    }
}
