package jenkins.benchmark.jmh;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Hudson;
import hudson.model.RootAction;
import hudson.security.ACL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.jvnet.hudson.test.JavaNetReverseProxy;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;
import org.jvnet.hudson.test.TestPluginManager;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Standard benchmark {@link State} for JMH when a Jenkins instance is required.
 * <p>
 * To use a Jenkins instance in your benchmark, your class containing benchmarks should have a public static inner
 * class that extends this class and should be annotated with {@link JmhBenchmark} to allow it to be automatically
 * discovered by {@link BenchmarkFinder}. To configure the instance, use {@link #setup()}.
 *
 * @see #setup()
 * @see #tearDown()
 * @see BenchmarkFinder
 * @since 2.50
 */
@State(Scope.Benchmark)
public abstract class JmhBenchmarkState implements RootAction {
    private static final Logger LOGGER = Logger.getLogger(JmhBenchmarkState.class.getName());
    private static final String contextPath = "/jenkins";

    private final TemporaryDirectoryAllocator temporaryDirectoryAllocator = new TemporaryDirectoryAllocator();
    private final AtomicInteger localPort = new AtomicInteger();

    private Jenkins jenkins = null;
    private Server server = null;

    /**
     * Sets up the temporary Jenkins instance for benchmarks.
     * <p>
     * One Jenkins instance is created for each fork of the benchmark.
     *
     * @throws Exception if unable to start the instance.
     */
    @Setup(org.openjdk.jmh.annotations.Level.Trial)
    public final void setupJenkins() throws Exception {
        // Set the jenkins.install.InstallState TEST to emulate
        // org.jvnet.hudson.test.JenkinsRule behaviour and avoid manual
        // security setup as in a default installation.
        System.setProperty("jenkins.install.state", "TEST");
        launchInstance();
        ACL.impersonate(ACL.SYSTEM);
        setup();
    }

    /**
     * Terminates the jenkins instance after the benchmark has completed its execution.
     * Run once for each Jenkins that was started.
     */
    @TearDown(org.openjdk.jmh.annotations.Level.Trial)
    public final void terminateJenkins() {
        try {
            tearDown();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception occurred during tearDown of Jenkins instance", e);
        } finally {
            JenkinsRule._stopJenkins(server, null, jenkins);
            try {
                JavaNetReverseProxy.getInstance().stop();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Unable to stop JavaNetReverseProxy server", e);
            }
            try {
                temporaryDirectoryAllocator.dispose();
            } catch (InterruptedException | IOException e) {
                LOGGER.log(Level.WARNING, "Unable to dispose temporary Jenkins directory" +
                                                  "that was started for benchmark", e);
            }
        }
    }

    private void launchInstance() throws Exception {
        server = JenkinsRule._createWebServer(
                contextPath,
                localPort::set,
                getClass().getClassLoader(),
                localPort.get(),
                JenkinsRule::_configureUserRealm);

        ServletContext webServer =
                server.getChildHandlerByClass(ContextHandler.class).getServletContext();

        jenkins = new Hudson(temporaryDirectoryAllocator.allocate(), webServer, TestPluginManager.INSTANCE);
        JenkinsRule._configureJenkinsForTest(jenkins);
        JenkinsRule._configureUpdateCenter(jenkins);
        jenkins.getActions().add(this);

        String url = Objects.requireNonNull(getJenkinsURL()).toString();
        Objects.requireNonNull(JenkinsLocationConfiguration.get()).setUrl(url);
        LOGGER.log(Level.INFO, "Running on {0}", url);
    }

    private URL getJenkinsURL() throws MalformedURLException {
        return new URL("http://localhost:" + localPort.get() + contextPath + "/");
    }

    /**
     * Get reference to the {@link Jenkins} started for the benchmark.
     * <p>
     * The instance can also be obtained using {@link Jenkins#getInstanceOrNull()}
     *
     * @return the Jenkins instance started for the benchmark.
     */
    public Jenkins getJenkins() {
        return jenkins;
    }

    /**
     * Override to setup resources required for the benchmark.
     * <p>
     * Runs before the benchmarks are run. At this state, the Jenkins instance
     * is ready to be worked upon and is available using {@link #getJenkins()}.
     * Does nothing by default.
     */
    public void setup() throws Exception {
        // noop
    }

    /**
     * Override to perform cleanup of resource initialized during setup.
     * <p>
     * Run before the Jenkins instance is terminated. Does nothing by default.
     */
    public void tearDown() {
        // noop
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "self";
    }
}
