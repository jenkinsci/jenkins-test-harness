package jenkins.jmh;

import hudson.WebAppMain;
import hudson.model.DownloadService;
import hudson.model.Hudson;
import hudson.model.JDK;
import hudson.model.RootAction;
import hudson.model.UpdateSite;
import hudson.security.ACL;
import hudson.util.PersistedList;
import io.vavr.Tuple2;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.eclipse.jetty.server.Server;
import org.jvnet.hudson.test.JavaNetReverseProxy;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import javax.annotation.CheckForNull;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@State(Scope.Benchmark)
public abstract class JmhBenchmarkState implements RootAction {
    private static final Logger LOGGER = Logger.getLogger(JmhBenchmarkState.class.getName());
    private static final String contextPath = "/jenkins";

    private final TemporaryDirectoryAllocator temporaryDirectoryAllocator = new TemporaryDirectoryAllocator();

    private AtomicInteger localPort = new AtomicInteger();
    private Jenkins jenkins = null;
    private Server server = null;

    // Run the setup for each individual fork of the JVM
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

    // Run the tearDown for each individual fork of the JVM
    @TearDown(org.openjdk.jmh.annotations.Level.Trial)
    public final void terminateJenkins() {
        try {
            tearDown();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception occurred during tearDown of Jenkins instance", e);
        } finally {
            if (jenkins != null && server != null) {
                try {
                    server.stop();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Exception occurred when shutting down server.", e);
                }

                jenkins.cleanUp();
                jenkins = null;
                server = null;
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
        Tuple2<Server, ServletContext> results = JenkinsRule._createWebServer(contextPath, localPort::set,
                this::getJenkinsURL, getClass().getClassLoader());
        server = results._1;

        ServletContext webServer = results._2;

        File jenkinsHome = temporaryDirectoryAllocator.allocate();
        jenkins = new Hudson(jenkinsHome, webServer);
        jenkins.setNoUsageStatistics(true);

        webServer.setAttribute("app", jenkins);
        webServer.setAttribute("version", "?");

        // configure Jelly views
        WebAppMain.installExpressionFactory(new ServletContextEvent(webServer));
        jenkins.getJDKs().add(new JDK("default", System.getProperty("java.home")));

        PersistedList<UpdateSite> sites = jenkins.getUpdateCenter().getSites();
        sites.clear();
        sites.add(new UpdateSite("default", "http://localhost:" +
                                                    JavaNetReverseProxy.getInstance().localPort + "/update-center.json"));

        DownloadService.neverUpdate = true;
        UpdateSite.neverUpdate = true;

        jenkins.getActions().add(this);

        Objects.requireNonNull(JenkinsLocationConfiguration.get()).setUrl(Objects.requireNonNull(getJenkinsURL()).toString());
    }

    private URL getJenkinsURL() {
        try {
            return new URL("http://localhost:" + localPort.get() + contextPath + "/");
        } catch (Exception e) {
            return null;
        }
    }

    public Jenkins getJenkins() {
        return jenkins;
    }

    /**
     * Override to setup resources required for the benchmark.
     * <p>
     * Runs before the benchmarks are run. At this state, the Jenkins
     * is ready to be worked upon.
     */
    public void setup() throws Exception {
    }

    /**
     * Override to perform cleanup of resource initialized during setup.
     * <p>
     * Run before the Jenkins instance is terminated.
     */
    public void tearDown() {
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
