/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 * Yahoo! Inc., Tom Huybrechts, Olivier Lamy
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

import com.gargoylesoftware.css.parser.CSSErrorHandler;
import com.gargoylesoftware.css.parser.CSSException;
import com.gargoylesoftware.css.parser.CSSParseException;
import com.gargoylesoftware.htmlunit.AjaxController;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.DefaultCssErrorHandler;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.WebClientUtil;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.WebResponseData;
import com.gargoylesoftware.htmlunit.WebResponseListener;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlImage;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.javascript.AbstractJavaScriptEngine;
import com.gargoylesoftware.htmlunit.javascript.HtmlUnitContextFactory;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine;
import com.gargoylesoftware.htmlunit.javascript.host.xml.XMLHttpRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.gargoylesoftware.htmlunit.util.WebResponseWrapper;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import com.google.common.net.HttpHeaders;
import hudson.ClassicPluginStrategy;
import hudson.CloseProofOutputStream;
import hudson.DNSMultiCast;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.Launcher;
import hudson.Main;
import hudson.PluginManager;
import hudson.Util;
import hudson.WebAppMain;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DownloadService;
import hudson.model.Executor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.JDK;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.RootAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.UpdateSite;
import hudson.model.User;
import hudson.model.View;
import hudson.remoting.Which;
import hudson.security.ACL;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.csrf.CrumbIssuer;
import hudson.slaves.ComputerConnector;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.tools.ToolProperty;
import hudson.util.PersistedList;
import hudson.util.ReflectionUtils;
import hudson.util.StreamTaskListener;
import hudson.util.jna.GNUCLibrary;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.jar.Manifest;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import jenkins.model.Jenkins;
import jenkins.model.JenkinsAdaptor;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.security.ApiTokenProperty;
import net.sf.json.JSONObject;
import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.ContextFactory;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.security.Password;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.*;
import org.junit.internal.AssumptionViolatedException;
import static org.junit.matchers.JUnitMatchers.containsString;
import org.junit.rules.MethodRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import com.gargoylesoftware.htmlunit.html.DomNodeUtil;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import hudson.console.PlainTextConsoleOutputStream;
import hudson.init.InitMilestone;
import hudson.model.Job;
import hudson.model.Slave;
import hudson.model.queue.QueueTaskFuture;
import hudson.slaves.JNLPLauncher;

import java.net.HttpURLConnection;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.ExecutionException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.security.MasterToSlaveCallable;
import org.hamcrest.core.IsInstanceOf;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.Timeout;
import org.junit.runners.model.TestTimedOutException;
import org.jvnet.hudson.test.recipes.Recipe;
import org.jvnet.hudson.test.recipes.WithTimeout;
import org.jvnet.hudson.test.rhino.JavaScriptDebugger;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.Dispatcher;
import org.kohsuke.stapler.MetaClass;
import org.kohsuke.stapler.MetaClassLoader;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.mozilla.javascript.tools.debugger.Dim;
import org.mozilla.javascript.tools.shell.Global;
import org.springframework.dao.DataAccessException;
import org.xml.sax.SAXException;

/**
 * JUnit rule to allow test cases to fire up a Jenkins instance.
 *
 * @see <a href="http://wiki.jenkins-ci.org/display/JENKINS/Unit+Test">Wiki article about unit testing in Jenkins</a>
 * @author Stephen Connolly
 * @since 1.436
 * @see RestartableJenkinsRule
 */
@SuppressWarnings({"deprecation","rawtypes"})
public class JenkinsRule implements TestRule, MethodRule, RootAction {

    protected TestEnvironment env;

    protected Description testDescription;

    /**
     * Points to the same object as {@link #jenkins} does.
     */
    @Deprecated
    public Hudson hudson;

    public Jenkins jenkins;

    protected HudsonHomeLoader homeLoader = HudsonHomeLoader.NEW;
    /**
     * TCP/IP port that the server is listening on.
     */
    protected int localPort;
    protected Server server;

    /**
     * Where in the {@link Server} is Jenkins deployed?
     * <p>
     * Just like {@link javax.servlet.ServletContext#getContextPath()}, starts with '/' but doesn't end with '/'.
     * Unlike {@link WebClient#getContextPath} this is not a complete URL.
     */
    public String contextPath = "/jenkins";

    /**
     * {@link Runnable}s to be invoked at {@link #after()} .
     */
    protected List<LenientRunnable> tearDowns = new ArrayList<LenientRunnable>();

    protected List<JenkinsRecipe.Runner> recipes = new ArrayList<JenkinsRecipe.Runner>();

    /**
     * Remember {@link WebClient}s that are created, to release them properly.
     */
    private List<WebClient> clients = new ArrayList<WebClient>();

    /**
     * JavaScript "debugger" that provides you information about the JavaScript call stack
     * and the current values of the local variables in those stack frame.
     *
     * <p>
     * Unlike Java debugger, which you as a human interfaces directly and interactively,
     * this JavaScript debugger is to be interfaced by your program (or through the
     * expression evaluation capability of your Java debugger.)
     */
    protected JavaScriptDebugger jsDebugger = new JavaScriptDebugger();

    /**
     * If this test case has additional {@link org.jvnet.hudson.test.recipes.WithPlugin} annotations, set to true.
     * This will cause a fresh {@link hudson.PluginManager} to be created for this test.
     * Leaving this to false enables the test harness to use a pre-loaded plugin manager,
     * which runs faster.
     *
     * @deprecated
     *      Use {@link #pluginManager}
     */
    public boolean useLocalPluginManager;

    /**
     * Number of seconds until the test times out.
     */
    public int timeout = Integer.getInteger("jenkins.test.timeout", new DisableOnDebug(null).isDebugging() ? 0 : 180);

    /**
     * Set the plugin manager to be passed to {@link Jenkins} constructor.
     *
     * For historical reasons, {@link #useLocalPluginManager}==true will take the precedence.
     */
    private PluginManager pluginManager = TestPluginManager.INSTANCE;

    public JenkinsComputerConnectorTester computerConnectorTester = new JenkinsComputerConnectorTester(this);

    private boolean origDefaultUseCache = true;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    public Jenkins getInstance() {
        return jenkins;
    }

    /**
     * Override to set up your specific external resource.
     * @throws Throwable if setup fails (which will disable {@code after}
     */
    public void before() throws Throwable {
        for (Handler h : Logger.getLogger("").getHandlers()) {
            if (h instanceof ConsoleHandler) {
                ((ConsoleHandler) h).setFormatter(new DeltaSupportLogFormatter());
            }
        }

        if (Thread.interrupted()) { // JENKINS-30395
            LOGGER.warning("was interrupted before start");
        }

        if(Functions.isWindows()) {
            // JENKINS-4409.
            // URLConnection caches handles to jar files by default,
            // and it prevents delete temporary directories on Windows.
            // Disables caching here.
            // Though defaultUseCache is a static field,
            // its setter and getter are provided as instance methods.
            URLConnection aConnection = new File(".").toURI().toURL().openConnection();
            origDefaultUseCache = aConnection.getDefaultUseCaches();
            aConnection.setDefaultUseCaches(false);
        }
        
        // Not ideal (https://github.com/junit-team/junit/issues/116) but basically works.
        if (Boolean.getBoolean("ignore.random.failures")) {
            RandomlyFails rf = testDescription.getAnnotation(RandomlyFails.class);
            if (rf != null) {
                throw new AssumptionViolatedException("Known to randomly fail: " + rf.value());
            }
        }

        env = new TestEnvironment(testDescription);
        env.pin();
        recipe();
        AbstractProject.WORKSPACE.toString();
        User.clear();

        try {
            Field theInstance = Jenkins.class.getDeclaredField("theInstance");
            theInstance.setAccessible(true);
            if (theInstance.get(null) != null) {
                LOGGER.warning("Jenkins.theInstance was not cleared by a previous test, doing that now");
                theInstance.set(null, null);
            }
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
        }

        try {
            jenkins = hudson = newHudson();
            // If the initialization graph is corrupted, we cannot expect that Jenkins is in the good shape.
            // Likely it is an issue in @Initializer() definitions (see JENKINS-37759).
            // So we just fail the test.
            if (jenkins.getInitLevel() != InitMilestone.COMPLETED) {
                throw new Exception("Jenkins initialization has not reached the COMPLETED initialization stage. Current state is " + jenkins.getInitLevel() +
                        ". Likely there is an issue with the Initialization task graph (e.g. usage of @Initializer(after = InitMilestone.COMPLETED)). See JENKINS-37759 for more info");
            }
        } catch (Exception e) {
            // if Hudson instance fails to initialize, it leaves the instance field non-empty and break all the rest of the tests, so clean that up.
            Field f = Jenkins.class.getDeclaredField("theInstance");
            f.setAccessible(true);
            f.set(null,null);
            throw e;
        }

        jenkins.setCrumbIssuer(new TestCrumbIssuer());  // TODO: Move to _configureJenkinsForTest after JENKINS-55240
        _configureJenkinsForTest(jenkins);
        configureUpdateCenter();

        // expose the test instance as a part of URL tree.
        // this allows tests to use a part of the URL space for itself.
        jenkins.getActions().add(this);

        JenkinsLocationConfiguration.get().setUrl(getURL().toString());
    }

    /**
     * Configures a Jenkins instance for test.
     *
     * @param jenkins jenkins instance which has to be configured
     * @throws Exception if unable to configure
     * @since 2.50
     */
    public static void _configureJenkinsForTest(Jenkins jenkins) throws Exception {
        jenkins.setNoUsageStatistics(true); // collecting usage stats from tests is pointless.
        jenkins.servletContext.setAttribute("app", jenkins);
        jenkins.servletContext.setAttribute("version", "?");
        WebAppMain.installExpressionFactory(new ServletContextEvent(jenkins.servletContext));

        // set a default JDK to be the one that the harness is using.
        jenkins.getJDKs().add(new JDK("default", System.getProperty("java.home")));
    }
    
    private static void dumpThreads() {
        ThreadInfo[] threadInfos = Functions.getThreadInfos();
        Functions.ThreadGroupMap m = Functions.sortThreadsAndGetGroupMap(threadInfos);
        for (ThreadInfo ti : threadInfos) {
            System.err.println(Functions.dumpThreadInfo(ti, m));
        }
    }

    /**
     * Configures the update center setting for the test.
     * By default, we load updates from local proxy to avoid network traffic as much as possible.
     */
    protected void configureUpdateCenter() throws Exception {
        _configureUpdateCenter(jenkins);
    }

    /**
     * Internal method used to configure update center to avoid network traffic.
     * @param jenkins the Jenkins to configure
     * @since 2.50
     */
    public static void _configureUpdateCenter(Jenkins jenkins) throws Exception {
        final String updateCenterUrl;
        jettyLevel(Level.WARNING);
        try {
            updateCenterUrl = "http://localhost:"+ JavaNetReverseProxy.getInstance().localPort+"/update-center.json";
        } finally {
            jettyLevel(Level.INFO);
        }

        // don't waste bandwidth talking to the update center
        DownloadService.neverUpdate = true;
        UpdateSite.neverUpdate = true;

        PersistedList<UpdateSite> sites = jenkins.getUpdateCenter().getSites();
        sites.clear();
        sites.add(new UpdateSite("default", updateCenterUrl));
    }

    /**
     * Override to tear down your specific external resource.
     */
    public void after() throws Exception {
        try {
            if (jenkins!=null) {
                for (EndOfTestListener tl : jenkins.getExtensionList(EndOfTestListener.class))
                    tl.onTearDown();
            }

            // cancel pending asynchronous operations, although this doesn't really seem to be working
            for (WebClient client : clients) {
                // unload the page to cancel asynchronous operations
                try {
                    client.getPage("about:blank");
                } catch (IOException e) {
                    // ignore
                }
                client.close();
            }
            clients.clear();

        } finally {
            _stopJenkins(server, tearDowns, jenkins);

            try {
                env.dispose();
            } catch (Exception x) {
                x.printStackTrace();
            }

            // Hudson creates ClassLoaders for plugins that hold on to file descriptors of its jar files,
            // but because there's no explicit dispose method on ClassLoader, they won't get GC-ed until
            // at some later point, leading to possible file descriptor overflow. So encourage GC now.
            // see http://bugs.sun.com/view_bug.do?bug_id=4950148
            // TODO use URLClassLoader.close() in Java 7
            System.gc();
            
            // restore defaultUseCache
            if(Functions.isWindows()) {
                URLConnection aConnection = new File(".").toURI().toURL().openConnection();
                aConnection.setDefaultUseCaches(origDefaultUseCache);
            }
        }
    }

    /**
     * Internal method to stop Jenkins instance.
     *
     * @param server    server on which Jenkins is running.
     * @param tearDowns tear down methods for tests
     * @param jenkins   the jenkins instance
     * @since 2.50
     */
    public static void _stopJenkins(Server server, List<LenientRunnable> tearDowns, Jenkins jenkins) {
        final RuntimeException exception = new RuntimeException("One or more problems while shutting down Jenkins");

        jettyLevel(Level.WARNING);
        try {
            server.stop();
        } catch (Exception e) {
            exception.addSuppressed(e);
        } finally {
            jettyLevel(Level.INFO);
        }

        if (tearDowns != null) {
            for (LenientRunnable r : tearDowns) {
                try {
                    r.run();
                } catch (Exception e) {
                    exception.addSuppressed(e);
                }
            }
        }

        if (jenkins != null)
            jenkins.cleanUp();
        ExtensionList.clearLegacyInstances();
        DescriptorExtensionList.clearLegacyInstances();

        if (exception.getSuppressed().length > 0) {
            throw exception;
        }
    }

    private static void jettyLevel(Level level) {
        Logger.getLogger("org.eclipse.jetty").setLevel(level);
    }

    /**
     * Backward compatibility with JUnit 4.8.
     */
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        return apply(base,Description.createTestDescription(method.getMethod().getDeclaringClass(), method.getName(), method.getAnnotations()));
    }

    public Statement apply(final Statement base, final Description description) {
        if (description.getAnnotation(WithoutJenkins.class) != null) {
            // request has been made to not create the instance for this test method
            return base;
        }
        Statement wrapped = new Statement() {
            @Override
            public void evaluate() throws Throwable {
                testDescription = description;
                Thread t = Thread.currentThread();
                String o = t.getName();
                t.setName("Executing "+ testDescription.getDisplayName());
                System.out.println("=== Starting " + testDescription.getDisplayName());
                before();
                try {
                    // so that test code has all the access to the system
                    ACL.impersonate(ACL.SYSTEM);
                    try {
                        base.evaluate();
                    } catch (Throwable th) {
                        // allow the late attachment of a debugger in case of a failure. Useful
                        // for diagnosing a rare failure
                        try {
                            throw new BreakException();
                        } catch (BreakException e) {}

                        RandomlyFails rf = testDescription.getAnnotation(RandomlyFails.class);
                        if (rf != null) {
                            System.err.println("Note: known to randomly fail: " + rf.value());
                        }

                        throw th;
                    }
                } finally {
                    after();
                    testDescription = null;
                    t.setName(o);
                }
            }
        };
        final int testTimeout = getTestTimeoutOverride(description);
        if (testTimeout <= 0) {
            System.out.println("Test timeout disabled.");
            return wrapped;
        } else {
            final Statement timeoutStatement = Timeout.seconds(testTimeout).apply(wrapped, description);
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        timeoutStatement.evaluate();
                    } catch (TestTimedOutException x) {
                        // withLookingForStuckThread does not work well; better to just have a full thread dump.
                        LOGGER.warning(String.format("Test timed out (after %d seconds).", testTimeout));
                        dumpThreads();
                        throw x;
                    }
                }
            };
        }
    }

    private int getTestTimeoutOverride(Description description) {
        WithTimeout withTimeout = description.getAnnotation(WithTimeout.class);
        return withTimeout != null ? withTimeout.value(): this.timeout;
    }

    @SuppressWarnings("serial")
    public static class BreakException extends Exception {}

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "self";
    }

    /**
     * Creates a new instance of {@link jenkins.model.Jenkins}. If the derived class wants to create it in a different way,
     * you can override it.
     */
    protected Hudson newHudson() throws Exception {
        jettyLevel(Level.WARNING);
        ServletContext webServer = createWebServer();
        File home = homeLoader.allocate();
        for (JenkinsRecipe.Runner r : recipes)
            r.decorateHome(this,home);
        try {
            return new Hudson(home, webServer, getPluginManager());
        } catch (InterruptedException x) {
            throw new AssumptionViolatedException("Jenkins startup interrupted", x);
        } finally {
            jettyLevel(Level.INFO);
        }
    }

    public PluginManager getPluginManager() {
        if (jenkins == null) {
            return useLocalPluginManager ? null : pluginManager;
        } else {
            return jenkins.getPluginManager();
        }
    }

    /**
     * Sets the {@link PluginManager} to be used when creating a new {@link Jenkins} instance.
     *
     * @param pluginManager
     *      null to let Jenkins create a new instance of default plugin manager, like it normally does when running as a webapp outside the test.
     */
    public void setPluginManager(PluginManager pluginManager) {
        this.useLocalPluginManager = false;
        this.pluginManager = pluginManager;
        if (jenkins!=null)
            throw new IllegalStateException("Too late to override the plugin manager");
    }

    public JenkinsRule with(PluginManager pluginManager) {
        setPluginManager(pluginManager);
        return this;
    }

    public File getWebAppRoot() throws Exception {
        return WarExploder.getExplodedDir();
    }

    /**
     * Prepares a webapp hosting environment to get {@link javax.servlet.ServletContext} implementation
     * that we need for testing.
     */
    protected ServletContext createWebServer() throws Exception {
        ImmutablePair<Server,  ServletContext> results = _createWebServer(contextPath,
                (x) -> localPort = x, getClass().getClassLoader(), localPort, this::configureUserRealm);
        server = results.left;
        LOGGER.log(Level.INFO, "Running on {0}", getURL());
        return results.right;
    }

    /**
     * Creates a web server on which Jenkins can run
     *
     * @param contextPath          the context path at which to put Jenkins
     * @param portSetter           the port on which the server runs will be set using this function
     * @param classLoader          the class loader for the {@link WebAppContext}
     * @param localPort            port on which the server runs
     * @param loginServiceSupplier configures the {@link LoginService} for the instance
     * @return ImmutablePair consisting of the {@link Server} and the {@link ServletContext}
     * @since 2.50
     */
    public static ImmutablePair<Server, ServletContext> _createWebServer(String contextPath, Consumer<Integer> portSetter,
                                                                         ClassLoader classLoader, int localPort,
                                                                         Supplier<LoginService> loginServiceSupplier)
            throws Exception {
        Server server = new Server(new ThreadPoolImpl(new ThreadPoolExecutor(10, 10, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("Jetty Thread Pool");
                return t;
            }
        })));

        WebAppContext context = new WebAppContext(WarExploder.getExplodedDir().getPath(), contextPath);
        context.setClassLoader(classLoader);
        context.setConfigurations(new Configuration[]{new WebXmlConfiguration()});
        context.addBean(new NoListenerConfiguration(context));
        server.setHandler(context);
        context.setMimeTypes(MIME_TYPES);
        context.getSecurityHandler().setLoginService(loginServiceSupplier.get());
        context.setResourceBase(WarExploder.getExplodedDir().getPath());

        ServerConnector connector = new ServerConnector(server, 1, 1);
        HttpConfiguration config = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();
        // use a bigger buffer as Stapler traces can get pretty large on deeply nested URL
        config.setRequestHeaderSize(12 * 1024);
        connector.setHost("localhost");
        if (System.getProperty("port") != null) {
            connector.setPort(Integer.parseInt(System.getProperty("port")));
        } else if (localPort != 0) {
            connector.setPort(localPort);
        }

        server.addConnector(connector);
        server.start();

        portSetter.accept(connector.getLocalPort());

        ServletContext servletContext =  context.getServletContext();
        return new ImmutablePair<>(server, servletContext);
    }

    /**
     * Configures a security realm for a test.
     */
    protected LoginService configureUserRealm() {
        return _configureUserRealm();
    }

    /**
     * Creates a {@link HashLoginService} with three users: alice, bob and charlie
     *
     * The password is same as the username
     * @return a new login service
     * @since 2.50
     */
    public static LoginService _configureUserRealm() {
        HashLoginService realm = new HashLoginService();
        realm.setName("default");   // this is the magic realm name to make it effective on everywhere
        UserStore userStore = new UserStore();
        realm.setUserStore( userStore );
        userStore.addUser("alice", new Password("alice"), new String[]{"user","female"});
        userStore.addUser("bob", new Password("bob"), new String[]{"user","male"});
        userStore.addUser("charlie", new Password("charlie"), new String[]{"user","male"});

        return realm;
    }

//
// Convenience methods
//

    /**
     * Creates a new job.
     *
     * @param type Top level item type.
     * @param name Item name.
     *
     * @throws IllegalArgumentException if the project of the given name already exists.
     */
    public <T extends TopLevelItem> T createProject(Class<T> type, String name) throws IOException {
        return jenkins.createProject(type, name);
    }

    /**
     * Creates a new job with an unique name.
     *
     * @param type Top level item type.
     */
    public <T extends TopLevelItem> T createProject(Class<T> type) throws IOException {
        return jenkins.createProject(type, createUniqueProjectName());
    }

    public FreeStyleProject createFreeStyleProject() throws IOException {
        return createFreeStyleProject(createUniqueProjectName());
    }

    public FreeStyleProject createFreeStyleProject(String name) throws IOException {
        return createProject(FreeStyleProject.class, name);
    }

    /**
     * Creates a simple folder that other jobs can be placed in.
     * @since 1.494
     */
    public MockFolder createFolder(String name) throws IOException {
        return createProject(MockFolder.class, name);
    }

    protected String createUniqueProjectName() {
        return "test"+jenkins.getItems().size();
    }

    /**
     * Creates {@link hudson.Launcher.LocalLauncher}. Useful for launching processes.
     */
    public Launcher.LocalLauncher createLocalLauncher() {
        return new Launcher.LocalLauncher(StreamTaskListener.fromStdout());
    }

    /**
     * Allocates a new temporary directory for the duration of this test.
     * @deprecated Use {@link TemporaryFolder} instead.
     */
    @Deprecated
    public File createTmpDir() throws IOException {
        return env.temporaryDirectoryAllocator.allocate();
    }

    public DumbSlave createSlave(boolean waitForChannelConnect) throws Exception {
        DumbSlave slave = createSlave();
        if (waitForChannelConnect) {
            long start = System.currentTimeMillis();
            while (slave.getChannel() == null) {
                if (System.currentTimeMillis() > (start + 10000)) {
                    throw new IllegalStateException("Timed out waiting on DumbSlave channel to connect.");
                }
                Thread.sleep(200);
            }
        }
        return slave;
    }

    public void disconnectSlave(DumbSlave slave) throws Exception {
        slave.getComputer().disconnect(new OfflineCause.ChannelTermination(new Exception("terminate")));
        long start = System.currentTimeMillis();
        while (slave.getChannel() != null) {
            if (System.currentTimeMillis() > (start + 10000)) {
                throw new IllegalStateException("Timed out waiting on DumbSlave channel to disconnect.");
            }
            Thread.sleep(200);
        }
    }

    public DumbSlave createSlave() throws Exception {
        return createSlave("",null);
    }

    /**
     * Creates and launches a new slave on the local host.
     */
    public DumbSlave createSlave(Label l) throws Exception {
    	return createSlave(l, null);
    }

    /**
     * Creates a test {@link hudson.security.SecurityRealm} that recognizes username==password as valid.
     * @see MockAuthorizationStrategy
     */
    public DummySecurityRealm createDummySecurityRealm() {
        return new DummySecurityRealm();
    }

    /** @see #createDummySecurityRealm */
    public static class DummySecurityRealm extends AbstractPasswordBasedSecurityRealm {

        private final Map<String,Set<String>> groupsByUser = new HashMap<String,Set<String>>();

        DummySecurityRealm() {}

        @Override
        protected UserDetails authenticate(String username, String password) throws AuthenticationException {
            if (username.equals(password))
                return loadUserByUsername(username);
            throw new BadCredentialsException(username);
        }

        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException,
                DataAccessException {
            List<GrantedAuthority> auths = new ArrayList<GrantedAuthority>();
            auths.add(AUTHENTICATED_AUTHORITY);
            Set<String> groups = groupsByUser.get(username);
            if (groups != null) {
                for (String g : groups) {
                    auths.add(new GrantedAuthorityImpl(g));
                }
            }
            return new org.acegisecurity.userdetails.User(username,"",true,true,true,true, auths.toArray(new GrantedAuthority[0]));
        }

        @Override
        public GroupDetails loadGroupByGroupname(final String groupname) throws UsernameNotFoundException, DataAccessException {
            for (Set<String> groups : groupsByUser.values()) {
                if (groups.contains(groupname)) {
                    return new GroupDetails() {
                        @Override
                        public String getName() {
                            return groupname;
                        }
                    };
                }
            }
            throw new UsernameNotFoundException(groupname);
        }

        /** Associate some groups with a username. */
        public void addGroups(String username, String... groups) {
            Set<String> gs = groupsByUser.get(username);
            if (gs == null) {
                groupsByUser.put(username, gs = new TreeSet<String>());
            }
            gs.addAll(Arrays.asList(groups));
        }

    }

    /**
     * Returns the URL of the webapp top page.
     * URL ends with '/'.
     */
    public URL getURL() throws IOException {
        return new URL("http://localhost:"+localPort+contextPath+"/");
    }

    public DumbSlave createSlave(EnvVars env) throws Exception {
        return createSlave("",env);
    }

    public DumbSlave createSlave(Label l, EnvVars env) throws Exception {
        return createSlave(l==null ? null : l.getExpression(), env);
    }

    /**
     * Creates a slave with certain additional environment variables
     */
    public DumbSlave createSlave(String labels, EnvVars env) throws Exception {
        synchronized (jenkins) {
            int sz = jenkins.getNodes().size();
            return createSlave("slave" + sz,labels,env);
    	}
    }

    public DumbSlave createSlave(String nodeName, String labels, EnvVars env) throws Exception {
        synchronized (jenkins) {
            DumbSlave slave = new DumbSlave(nodeName, "dummy",
    				createTmpDir().getPath(), "1", Node.Mode.NORMAL, labels==null?"":labels, createComputerLauncher(env), RetentionStrategy.NOOP, Collections.EMPTY_LIST);                        
    		jenkins.addNode(slave);
    		return slave;
    	}
    }

    public PretendSlave createPretendSlave(FakeLauncher faker) throws Exception {
        synchronized (jenkins) {
            int sz = jenkins.getNodes().size();
            PretendSlave slave = new PretendSlave("slave" + sz, createTmpDir().getPath(), "", createComputerLauncher(null), faker);
    		jenkins.addNode(slave);
    		return slave;
        }
    }

    /**
     * Creates a launcher for starting a local agent.
     *
     * @param env
     *      Environment variables to add to the slave process. Can be null.
     */
    public ComputerLauncher createComputerLauncher(EnvVars env) throws URISyntaxException, IOException {
        int sz = jenkins.getNodes().size();
        return new SimpleCommandLauncher(
                String.format("\"%s/bin/java\" %s %s -jar \"%s\"",
                        System.getProperty("java.home"),
                        SLAVE_DEBUG_PORT>0 ? " -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address="+(SLAVE_DEBUG_PORT+sz): "",
                        "-Djava.awt.headless=true",
                        new File(jenkins.getJnlpJars("slave.jar").getURL().toURI()).getAbsolutePath()),
                env);
    }

    /**
     * Create a new slave on the local host and wait for it to come online
     * before returning.
     */
    public DumbSlave createOnlineSlave() throws Exception {
        return createOnlineSlave(null);
    }

    /**
     * Create a new slave on the local host and wait for it to come online
     * before returning.
     */
    public DumbSlave createOnlineSlave(Label l) throws Exception {
        return createOnlineSlave(l, null);
    }

    /**
     * Create a new slave on the local host and wait for it to come online
     * before returning
     * @see #waitOnline
     */
    @SuppressWarnings({"deprecation"})
    public DumbSlave createOnlineSlave(Label l, EnvVars env) throws Exception {
        DumbSlave s = createSlave(l, env);
        waitOnline(s);
        return s;
    }

    /**
     * Waits for a newly created slave to come online.
     * @see #createSlave()
     */
    public void waitOnline(Slave s) throws Exception {
        Computer computer = s.toComputer();
        if (s.getLauncher() instanceof JNLPLauncher) {
            while (!computer.isOnline()) {
                Thread.sleep(100);
            }
            return;
        }
        try {
            computer.connect(false).get();
        } catch (ExecutionException x) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PlainTextConsoleOutputStream ptcos = new PlainTextConsoleOutputStream(baos);
            ptcos.write(computer.getLog().getBytes());
            throw new AssertionError("failed to connect " + s.getNodeName() + ": " + baos, x);
        }
    }

    /**
     * Same as {@link #showAgentLogs(Slave, Map)} but taking a preconfigured list of loggers as a convenience.
     */
    public void showAgentLogs(Slave s, LoggerRule loggerRule) throws Exception {
        showAgentLogs(s, loggerRule.getRecordedLevels());
    }

    /**
     * Forward agent logs to standard error of the test process.
     * Otherwise log messages would be sent only to {@link Computer#getLogText} etc.,
     * or discarded entirely (if below {@link Level#INFO}).
     * @param s an <em>online</em> agent
     * @param loggers {@link Logger#getName} tied to log level
     */
    public void showAgentLogs(Slave s, Map<String, Level> loggers) throws Exception {
        s.getChannel().call(new RemoteLogDumper(s.getNodeName(), loggers));
    }

    private static final class RemoteLogDumper extends MasterToSlaveCallable<Void, RuntimeException> {
        private final String name;
        private final Map<String, Level> loggers;
        private final TaskListener stderr = StreamTaskListener.fromStderr();
        RemoteLogDumper(String name, Map<String, Level> loggers) {
            this.name = name;
            this.loggers = loggers;
        }
        @Override public Void call() throws RuntimeException {
            Handler handler = new Handler() {
                final Formatter formatter = new DeltaSupportLogFormatter();
                @Override public void publish(LogRecord record) {
                    if (isLoggable(record)) {
                        stderr.getLogger().print(formatter.format(record).replaceAll("(?m)^", "[" + name + "] "));
                    }
                }
                @Override public void flush() {}
                @Override public void close() throws SecurityException {}
            };
            handler.setLevel(Level.ALL);
            loggers.entrySet().forEach(e -> {
                Logger logger = Logger.getLogger(e.getKey());
                logger.setLevel(e.getValue());
                logger.addHandler(handler);
            });
            return null;
        }
    }

    /**
     * Blocks until the ENTER key is hit.
     * This is useful during debugging a test so that one can inspect the state of Hudson through the web browser.
     */
    public void interactiveBreak() throws Exception {
        System.out.println("Jenkins is running at " + getURL());
        new BufferedReader(new InputStreamReader(System.in)).readLine();
    }

    /**
     * Returns the last item in the list.
     */
    public <T> T last(List<T> items) {
        return items.get(items.size()-1);
    }

    /**
     * Pauses the execution until ENTER is hit in the console.
     * <p>
     * This is often very useful so that you can interact with Hudson
     * from an browser, while developing a test case.
     */
    public void pause() throws IOException {
        new BufferedReader(new InputStreamReader(System.in)).readLine();
    }

    /**
     * Performs a search from the search box.
     */
    public Page search(String q) throws Exception {
        return new WebClient().search(q);
    }

    /**
     * Get JSON from A Jenkins endpoint.
     * @param path The endpoint URL.
     * @return The JSON.
     */
    public JSONWebResponse getJSON(@Nonnull String path) throws IOException {
        assert !path.startsWith("/");

        JenkinsRule.WebClient webClient = createWebClient();
        Page runsPage = null;
        try {
            runsPage = webClient.goTo(path, "application/json");
        } catch (SAXException e) {
            // goTo shouldn't be throwing a SAXException for JSON.
            throw new IllegalStateException("Unexpected SAXException.", e);
        }
        WebResponse webResponse = runsPage.getWebResponse();

        return new JSONWebResponse(webResponse);
    }

    /**
     * POST a JSON payload to a URL on the underlying Jenkins instance.
     * @param path The url path on Jenkins.
     * @param json An object that produces a JSON string from it's {@code toString} method.
     * @return A JSON response.
     * @throws IOException
     * @throws SAXException
     */
    public JSONWebResponse postJSON(@Nonnull String path, @Nonnull Object json) throws IOException, SAXException {
        assert !path.startsWith("/");

        URL postUrl = new URL(getURL().toExternalForm() + path);
        HttpURLConnection conn = (HttpURLConnection) postUrl.openConnection();

        conn.setDoOutput(true);
        long startTime = System.currentTimeMillis();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            NameValuePair crumb = getCrumbHeaderNVP();
            conn.setRequestProperty(crumb.getName(), crumb.getValue());

            byte[] content = json.toString().getBytes(UTF8);
            conn.setRequestProperty("Content-Length", String.valueOf(content.length));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(content);
                os.flush();
            }

            WebResponseData webResponseData;
            InputStream responseStream = conn.getInputStream();
            try {
                if (responseStream != null) {
                    byte[] bytes = IOUtils.toByteArray(responseStream);
                    webResponseData = new WebResponseData(bytes, conn.getResponseCode(), conn.getResponseMessage(), extractHeaders(conn));
                } else {
                    webResponseData = new WebResponseData(new byte[0], conn.getResponseCode(), conn.getResponseMessage(), extractHeaders(conn));
                }
            } finally {
                IOUtils.closeQuietly(responseStream);
            }

            WebResponse webResponse = new WebResponse(webResponseData, postUrl, HttpMethod.POST, (System.currentTimeMillis() - startTime));

            return new JSONWebResponse(webResponse);
        } finally {
            conn.disconnect();
        }
    }

    private List<NameValuePair> extractHeaders(HttpURLConnection conn) {
        List<NameValuePair> headers = new ArrayList<NameValuePair>();
        Set<Map.Entry<String,List<String>>> headerFields = conn.getHeaderFields().entrySet();
        for (Map.Entry<String,List<String>> headerField : headerFields) {
            String name = headerField.getKey();
            if (name != null) { // Yes, the header name can be null.
                List<String> values = headerField.getValue();
                for (String value : values) {
                    if (value != null) {
                        headers.add(new NameValuePair(name, value));
                    }
                }
            }
        }
        return headers;
    }

    /**
     * Convenience wrapper for JSON responses.
     */
    public static class JSONWebResponse extends WebResponseWrapper {

        public JSONWebResponse(WebResponse webResponse) throws IllegalArgumentException {
            super(webResponse);
        }

        public JSONObject getJSONObject() {
            String json = getContentAsString();
            return JSONObject.fromObject(json);
        }
    }

    /**
     * Hits the Hudson system configuration and submits without any modification.
     */
    public void configRoundtrip() throws Exception {
        submit(createWebClient().goTo("configure").getFormByName("config"));
    }

    /**
     * Loads a configuration page and submits it without any modifications, to
     * perform a round-trip configuration test.
     * <p>
     * See http://wiki.jenkins-ci.org/display/JENKINS/Unit+Test#UnitTest-Configurationroundtriptesting
     */
    public <P extends Item> P configRoundtrip(P job) throws Exception {
        submit(createWebClient().getPage(job, "configure").getFormByName("config"));
        return job;
    }

    /**
     * Performs a configuration round-trip testing for a builder.
     */
    public <B extends Builder> B configRoundtrip(B before) throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(before);
        configRoundtrip((Item)p);
        return (B)p.getBuildersList().get(before.getClass());
    }

    /**
     * Performs a configuration round-trip testing for a publisher.
     */
    public <P extends Publisher> P configRoundtrip(P before) throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getPublishersList().add(before);
        configRoundtrip((Item) p);
        return (P)p.getPublishersList().get(before.getClass());
    }

    public <C extends ComputerConnector> C configRoundtrip(C before) throws Exception {
        computerConnectorTester.connector = before;
        submit(createWebClient().goTo("self/computerConnectorTester/configure").getFormByName("config"));
        return (C)computerConnectorTester.connector;
    }

    public User configRoundtrip(User u) throws Exception {
        submit(createWebClient().goTo(u.getUrl()+"/configure").getFormByName("config"));
        return u;
    }

    public <N extends Node> N configRoundtrip(N node) throws Exception {
        submit(createWebClient().goTo("computer/" + node.getNodeName() + "/configure").getFormByName("config"));
        return (N)jenkins.getNode(node.getNodeName());
    }

    public <V extends View> V configRoundtrip(V view) throws Exception {
        submit(createWebClient().getPage(view, "configure").getFormByName("viewConfig"));
        return view;
    }


    /**
     * Asserts that the outcome of the build is a specific outcome.
     */
    public <R extends Run> R assertBuildStatus(Result status, R r) throws Exception {
        if(status==r.getResult())
            return r;

        // dump the build output in failure message (in case BuildWatcher is not being used)
        String msg = "unexpected build status; build log was:\n------\n" + getLog(r) + "\n------\n";
        assertThat(msg, r.getResult(), is(status));
        return r;
    }

    public <R extends Run> R assertBuildStatus(Result status, Future<? extends R> r) throws Exception {
        assertThat("build was actually scheduled", r, Matchers.notNullValue());
        return assertBuildStatus(status, r.get());
    }

    /** Determines whether the specified HTTP status code is generally "good" */
    public boolean isGoodHttpStatus(int status) {
        if ((400 <= status) && (status <= 417)) {
            return false;
        }
        if ((500 <= status) && (status <= 505)) {
            return false;
        }
        return true;
    }

    /** Assert that the specified page can be served with a "good" HTTP status,
     * eg, the page is not missing and can be served without a server error
     * @param page
     */
    public void assertGoodStatus(Page page) {
        assertThat(isGoodHttpStatus(page.getWebResponse().getStatusCode()), is(true));
    }


    public <R extends Run> R assertBuildStatusSuccess(R r) throws Exception {
        assertBuildStatus(Result.SUCCESS, r);
        return r;
    }

    public <R extends Run> R assertBuildStatusSuccess(Future<? extends R> r) throws Exception {
        return assertBuildStatus(Result.SUCCESS, r);
    }

    public <J extends Job<J,R>,R extends Run<J,R>> R buildAndAssertSuccess(final J job) throws Exception {
        assertThat(job, IsInstanceOf.instanceOf(ParameterizedJobMixIn.ParameterizedJob.class));
        QueueTaskFuture f = new ParameterizedJobMixIn() {
            @Override protected Job asJob() {
                return job;
            }
        }.scheduleBuild2(0);
        @SuppressWarnings("unchecked") // no way to make this compile checked
        Future<R> f2 = f;
        return assertBuildStatusSuccess(f2);
    }

    /**
     * Avoids need for cumbersome {@code this.<J,R>buildAndAssertSuccess(...)} type hints under JDK 7 javac (and supposedly also IntelliJ).
     */
    public FreeStyleBuild buildAndAssertSuccess(FreeStyleProject job) throws Exception {
        return assertBuildStatusSuccess(job.scheduleBuild2(0));
    }

    /**
     * Asserts that the console output of the build contains the given substring.
     */
    public void assertLogContains(String substring, Run run) throws IOException {
        assertThat(getLog(run), containsString(substring));
    }

    /**
     * Asserts that the console output of the build does not contain the given substring.
     */
    public void assertLogNotContains(String substring, Run run) throws IOException {
        assertThat(getLog(run), not(containsString(substring)));
    }

    /**
     * Get entire log file as plain text.
     * {@link Run#getLog()} is deprecated for reasons that are irrelevant in tests,
     * and also does not strip console annotations which are a distraction in test output.
     */
    public static String getLog(Run run) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            run.getLogText().writeLogTo(0, baos);
        } catch (FileNotFoundException x) {
            return ""; // log file not yet created, OK
        }
        return baos.toString(run.getCharset().name());
    }

    /**
     * Waits for a build to complete.
     * Useful in conjunction with {@link BuildWatcher}.
     * @return the same build, once done
     * @since 1.607
     */
    public <R extends Run<?,?>> R waitForCompletion(R r) throws InterruptedException {
        // Could be using com.jayway.awaitility:awaitility but it seems like overkill here.
        while (r.isBuilding()) {
            Thread.sleep(100);
        }
        return r;
    }

    /**
     * Waits for a build log to contain a specified string.
     * Useful in conjunction with {@link BuildWatcher}.
     * @return the same build, once it does
     * @since 1.607
     */
    public <R extends Run<?,?>> R waitForMessage(String message, R r) throws IOException, InterruptedException {
        while (!getLog(r).contains(message)) {
            if (!r.isLogUpdated()) {
                assertLogContains(message, r); // should now fail
            }
            Thread.sleep(100);
        }
        return r;
    }

    /**
     * Asserts that the XPath matches.
     */
    public void assertXPath(HtmlPage page, String xpath) {
        HtmlElement documentElement = page.getDocumentElement();
        assertNotNull("There should be an object that matches XPath:" + xpath,
                DomNodeUtil.selectSingleNode(documentElement, xpath));
    }

    /** Asserts that the XPath matches the contents of a DomNode page. This
     * variant of assertXPath(HtmlPage page, String xpath) allows us to
     * examine XmlPages.
     * @param page
     * @param xpath
     */
    public void assertXPath(DomNode page, String xpath) {
        List<? extends Object> nodes = page.getByXPath(xpath);
        assertThat("There should be an object that matches XPath:" + xpath, nodes.isEmpty(), is(false));
    }

    public void assertXPathValue(DomNode page, String xpath, String expectedValue) {
        Object node = page.getFirstByXPath(xpath);
        assertNotNull("no node found", node);
        assertTrue("the found object was not a Node " + xpath, node instanceof org.w3c.dom.Node);

        org.w3c.dom.Node n = (org.w3c.dom.Node) node;
        String textString = n.getTextContent();
        assertEquals("xpath value should match for " + xpath, expectedValue, textString);
    }

    public void assertXPathValueContains(DomNode page, String xpath, String needle) {
        Object node = page.getFirstByXPath(xpath);
        assertNotNull("no node found", node);
        assertTrue("the found object was not a Node " + xpath, node instanceof org.w3c.dom.Node);

        org.w3c.dom.Node n = (org.w3c.dom.Node) node;
        String textString = n.getTextContent();
        assertTrue("needle found in haystack", textString.contains(needle));
    }

    public void assertXPathResultsContainText(DomNode page, String xpath, String needle) {
        List<? extends Object> nodes = page.getByXPath(xpath);
        assertThat("no nodes matching xpath found", nodes.isEmpty(), is(false));
        boolean found = false;
        for (Object o : nodes) {
            if (o instanceof org.w3c.dom.Node) {
                org.w3c.dom.Node n = (org.w3c.dom.Node) o;
                String textString = n.getTextContent();
                if ((textString != null) && textString.contains(needle)) {
                    found = true;
                    break;
                }
            }
        }
        assertThat("needle found in haystack", found, is(true));
    }

    /**
     * Makes sure that all the images in the page loads successfully.
     * (By default, HtmlUnit doesn't load images.)
     */
    public void assertAllImageLoadSuccessfully(HtmlPage p) {
        for (HtmlImage img : DomNodeUtil.<HtmlImage>selectNodes(p, "//IMG")) {
            try {
                img.getHeight();
            } catch (IOException e) {
                throw new Error("Failed to load "+img.getSrcAttribute(),e);
            }
        }
    }


    public void assertStringContains(String message, String haystack, String needle) {
        assertThat(message, haystack, Matchers.containsString(needle));
    }

    public void assertStringContains(String haystack, String needle) {
        assertThat(haystack, Matchers.containsString(needle));
    }

    /**
     * Asserts that help files exist for the specified properties of the given instance.
     *
     * @param type
     *      The describable class type that should have the associated help files.
     * @param properties
     *      ','-separated list of properties whose help files should exist.
     */
    public void assertHelpExists(final Class<? extends Describable> type, final String properties) throws Exception {
        executeOnServer(new Callable<Object>() {
            public Object call() throws Exception {
                Descriptor d = jenkins.getDescriptor(type);
                WebClient wc = createWebClient();
                for (String property : listProperties(properties)) {
                    String url = d.getHelpFile(property);
                    assertThat("Help file for the property " + property + " is missing on " + type, url,
                            Matchers.notNullValue());
                    wc.goTo(url); // make sure it successfully loads
                }
                return null;
            }
        });
    }

    /**
     * Tokenizes "foo,bar,zot,-bar" and returns "foo,zot" (the token that starts with '-' is handled as
     * a cancellation.
     */
    private List<String> listProperties(String properties) {
        List<String> props = new CopyOnWriteArrayList<>(properties.split(","));
        for (String p : props) {
            if (p.startsWith("-")) {
                props.remove(p);
                props.remove(p.substring(1));
            }
        }
        return props;
    }

    /**
     * Submits the form.
     *
     * Plain {@link HtmlForm#submit(com.gargoylesoftware.htmlunit.html.SubmittableElement)} doesn't work correctly due to the use of YUI in Hudson.
     */
    public HtmlPage submit(HtmlForm form) throws Exception {
        return (HtmlPage) HtmlFormUtil.submit(form);
    }

    /**
     * Submits the form by clicking the submit button of the given name.
     *
     * @param name
     *      This corresponds to the @name of {@code <f:submit />}
     */
    public HtmlPage submit(HtmlForm form, String name) throws Exception {
        for( HtmlElement e : form.getElementsByTagName("button")) {
            HtmlElement p = (HtmlElement)e.getParentNode().getParentNode();                        
            if (p.getAttribute("name").equals(name) && HtmlElementUtil.hasClassName(p, "yui-submit-button")) {
                // For YUI handled submit buttons, just do a click.
                return (HtmlPage) HtmlElementUtil.click(e);
            } else if (e.getAttribute("name").equals(name)) {
                return (HtmlPage) HtmlFormUtil.submit(form, e);
            }
        }
        throw new AssertionError("No such submit button with the name "+name);
    }

    public HtmlInput findPreviousInputElement(HtmlElement current, String name) {
        return DomNodeUtil.selectSingleNode(current, "(preceding::input[@name='_."+name+"'])[last()]");
    }

    public HtmlButton getButtonByCaption(HtmlForm f, String s) {
        for (HtmlElement b : f.getElementsByTagName("button")) {
            if(b.getTextContent().trim().equals(s))
                return (HtmlButton)b;
        }
        return null;
    }

    /**
     * Creates a {@link TaskListener} connected to stdout.
     */
    public TaskListener createTaskListener() {
        return new StreamTaskListener(new CloseProofOutputStream(System.out));
    }

    /**
     * Asserts that two JavaBeans are equal as far as the given list of properties are concerned.
     *
     * <p>
     * This method takes two objects that have properties (getXyz, isXyz, or just the public xyz field),
     * and makes sure that the property values for each given property are equals (by using {@link org.junit.Assert#assertThat(Object, org.hamcrest.Matcher)})
     *
     * <p>
     * Property values can be null on both objects, and that is OK, but passing in a property that doesn't
     * exist will fail an assertion.
     *
     * <p>
     * This method is very convenient for comparing a large number of properties on two objects,
     * for example to verify that the configuration is identical after a config screen roundtrip.
     *
     * @param lhs
     *      One of the two objects to be compared.
     * @param rhs
     *      The other object to be compared
     * @param properties
     *      ','-separated list of property names that are compared.
     * @since 1.297
     */
    public void assertEqualBeans(Object lhs, Object rhs, String properties) throws Exception {
        assertThat("LHS", lhs, notNullValue());
        assertThat("RHS", rhs, notNullValue());
        for (String p : properties.split(",")) {
            PropertyDescriptor pd = PropertyUtils.getPropertyDescriptor(lhs, p);
            Object lp,rp;
            if(pd==null) {
                // field?
                try {
                    Field f = lhs.getClass().getField(p);
                    lp = f.get(lhs);
                    rp = f.get(rhs);
                } catch (NoSuchFieldException e) {
                    assertThat("No such property " + p + " on " + lhs.getClass(), pd, notNullValue());
                    return;
                }
            } else {
                lp = PropertyUtils.getProperty(lhs, p);
                rp = PropertyUtils.getProperty(rhs, p);
            }

            if (lp!=null && rp!=null && lp.getClass().isArray() && rp.getClass().isArray()) {
                // deep array equality comparison
                int m = Array.getLength(lp);
                int n = Array.getLength(rp);
                assertThat("Array length is different for property " + p, n, is(m));
                for (int i=0; i<m; i++)
                    assertThat(p + "[" + i + "] is different", Array.get(rp, i), is(Array.get(lp,i)));
                return;
            }

            assertThat("Property " + p + " is different", rp, is(lp));
        }
    }

    public void setQuietPeriod(int qp) {
        JenkinsAdaptor.setQuietPeriod(jenkins, qp);
    }

    /**
     * Works like {@link #assertEqualBeans(Object, Object, String)} but figure out the properties
     * via {@link org.kohsuke.stapler.DataBoundConstructor} and {@link org.kohsuke.stapler.DataBoundSetter}
     */
    public void assertEqualDataBoundBeans(Object lhs, Object rhs) throws Exception {
        if (lhs==null && rhs==null)     return;
        if (lhs==null)      fail("lhs is null while rhs="+rhs);
        if (rhs==null)      fail("rhs is null while lhs="+lhs);

        Constructor<?> lc = findDataBoundConstructor(lhs.getClass());
        Constructor<?> rc = findDataBoundConstructor(rhs.getClass());
        assertThat("Data bound constructor mismatch. Different type?", (Constructor)rc, is((Constructor)lc));

        String[] names = ClassDescriptor.loadParameterNames(lc);
        Class<?>[] types = lc.getParameterTypes();
        assertThat(types.length, is(names.length));
        assertEqualProperties(lhs, rhs, names, types);

        Map<String, Class<?>> lprops = extractDataBoundSetterProperties(lhs.getClass());
        Map<String, Class<?>> rprops = extractDataBoundSetterProperties(rhs.getClass());
        assertThat("Data bound setters mismatch. Different type?", lprops, is(rprops));
        List<String> setterNames = new ArrayList<String>();
        List<Class<?>> setterTypes = new ArrayList<Class<?>>();
        for (Map.Entry<String, Class<?>> e : lprops.entrySet()) {
            setterNames.add(e.getKey());
            setterTypes.add(e.getValue());
        }
        assertEqualProperties(lhs, rhs, setterNames.toArray(new String[0]), setterTypes.toArray(new Class<?>[0]));
    }

    private void assertEqualProperties(@Nonnull Object lhs, @Nonnull Object rhs, @Nonnull String[] names, @Nonnull Class<?>[] types) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, Exception {
        List<String> primitiveProperties = new ArrayList<String>();

        for (int i=0; i<types.length; i++) {
            Object lv = ReflectionUtils.getPublicProperty(lhs, names[i]);
            Object rv = ReflectionUtils.getPublicProperty(rhs, names[i]);

            if (lv != null && rv != null && Iterable.class.isAssignableFrom(types[i])) {
                Iterable lcol = (Iterable) lv;
                Iterable rcol = (Iterable) rv;
                Iterator ltr,rtr;
                for (ltr=lcol.iterator(), rtr=rcol.iterator(); ltr.hasNext() && rtr.hasNext();) {
                    Object litem = ltr.next();
                    Object ritem = rtr.next();

                    if (findDataBoundConstructor(litem.getClass())!=null) {
                        assertEqualDataBoundBeans(litem,ritem);
                    } else {
                        assertThat(ritem, is(litem));
                    }
                }
                assertThat("collection size mismatch between " + lhs + " and " + rhs, ltr.hasNext() ^ rtr.hasNext(),
                        is(false));
            } else
            if (findDataBoundConstructor(types[i])!=null || (lv!=null && findDataBoundConstructor(lv.getClass())!=null) || (rv!=null && findDataBoundConstructor(rv.getClass())!=null)) {
                // recurse into nested databound objects
                assertEqualDataBoundBeans(lv,rv);
            } else {
                primitiveProperties.add(names[i]);
            }
        }

        // compare shallow primitive properties
        if (!primitiveProperties.isEmpty())
            assertEqualBeans(lhs,rhs,Util.join(primitiveProperties,","));
    }

    @Nonnull
    private Map<String, Class<?>> extractDataBoundSetterProperties(@Nonnull Class<?> c) {
        Map<String, Class<?>> ret = new HashMap<String, Class<?>>();
        for ( ;c != null; c = c.getSuperclass()) {

            for (Field f: c.getDeclaredFields()) {
                if (f.getAnnotation(DataBoundSetter.class) == null) {
                    continue;
                }
                f.setAccessible(true);
                ret.put(f.getName(), f.getType());
           }

            for (Method m: c.getDeclaredMethods()) {
                AbstractMap.SimpleEntry<String, Class<?>> nameAndType = extractDataBoundSetter(m);
                if (nameAndType == null) {
                    continue;
                }
                if (ret.containsKey(nameAndType.getKey())) {
                    continue;
                }
                ret.put(nameAndType.getKey(),  nameAndType.getValue());
            }
        }
        return ret;
    }

    @CheckForNull
    private AbstractMap.SimpleEntry<String, Class<?>> extractDataBoundSetter(@Nonnull Method m) {
        // See org.kohsuke.stapler.RequestImpl::findDataBoundSetter
        if (!Modifier.isPublic(m.getModifiers())) {
            return null;
        }
        if (!m.getName().startsWith("set")) {
            return null;
        }
        if (m.getParameterTypes().length != 1) {
            return null;
        }
        if (!m.isAnnotationPresent(DataBoundSetter.class)) {
            return null;
        }
        
        // setXyz -> xyz
        return new AbstractMap.SimpleEntry<String, Class<?>>(
                Introspector.decapitalize(m.getName().substring(3)),
                m.getParameterTypes()[0]
        );
    }

    /**
     * Makes sure that two collections are identical via {@link #assertEqualDataBoundBeans(Object, Object)}
     */
    public void assertEqualDataBoundBeans(List<?> lhs, List<?> rhs) throws Exception {
        assertThat(rhs.size(), is(lhs.size()));
        for (int i=0; i<lhs.size(); i++)
            assertEqualDataBoundBeans(lhs.get(i),rhs.get(i));
    }

    public Constructor<?> findDataBoundConstructor(Class<?> c) {
        for (Constructor<?> m : c.getConstructors()) {
            if (m.getAnnotation(DataBoundConstructor.class)!=null)
                return m;
        }
        return null;
    }

    /**
     * Gets the descriptor instance of the current Hudson by its type.
     */
    public <T extends Descriptor<?>> T get(Class<T> d) {
        return jenkins.getDescriptorByType(d);
    }


    /**
     * Returns true if Hudson is building something or going to build something.
     */
    public boolean isSomethingHappening() {
        if (!jenkins.getQueue().isEmpty())
            return true;
        for (Computer n : jenkins.getComputers())
            if (!n.isIdle())
                return true;
        return false;
    }

    /**
     * Waits until Hudson finishes building everything, including those in the queue.
     */
    public void waitUntilNoActivity() throws Exception {
        waitUntilNoActivityUpTo(Integer.MAX_VALUE);
    }

    /**
     * Waits until Hudson finishes building everything, including those in the queue, or fail the test
     * if the specified timeout milliseconds is
     */
    public void waitUntilNoActivityUpTo(int timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        int streak = 0;

        while (true) {
            Thread.sleep(10);
            if (isSomethingHappening())
                streak=0;
            else
                streak++;

            if (streak>5)   // the system is quiet for a while
                return;

            if (System.currentTimeMillis()-startTime > timeout) {
                List<Queue.Executable> building = new ArrayList<Queue.Executable>();
                for (Computer c : jenkins.getComputers()) {
                    for (Executor e : c.getExecutors()) {
                        if (e.isBusy())
                            building.add(e.getCurrentExecutable());
                    }
                    for (Executor e : c.getOneOffExecutors()) {
                        if (e.isBusy())
                            building.add(e.getCurrentExecutable());
                    }
                }
                dumpThreads();
                throw new AssertionError(String.format("Jenkins is still doing something after %dms: queue=%s building=%s",
                        timeout, Arrays.asList(jenkins.getQueue().getItems()), building));
            }
        }
    }


//
// recipe methods. Control the test environments.
//

    /**
     * Called during the {@link #before()} to give a test case an opportunity to
     * control the test environment in which Hudson is run.
     *
     * <p>
     * One could override this method and call a series of {@code withXXX} methods,
     * or you can use the annotations with {@link Recipe} meta-annotation.
     */
    public void recipe() throws Exception {
        recipeLoadCurrentPlugin();
        // look for recipe meta-annotation
        try {
            for (final Annotation a : testDescription.getAnnotations()) {
                JenkinsRecipe r = a.annotationType().getAnnotation(JenkinsRecipe.class);
                if(r==null)     continue;
                final JenkinsRecipe.Runner runner = r.value().newInstance();
                recipes.add(runner);
                tearDowns.add(new LenientRunnable() {
                    public void run() throws Exception {
                        runner.tearDown(JenkinsRule.this,a);
                    }
                });
                runner.setup(this,a);
            }
        } catch (NoSuchMethodException e) {
            // not a plain JUnit test.
        }
    }

    /**
     * If this test harness is launched for a Jenkins plugin, locate the <tt>target/test-classes/the.jpl</tt>
     * and add a recipe to install that to the new Jenkins.
     *
     * <p>
     * This file is created by <tt>maven-hpi-plugin</tt> at the testCompile phase when the current
     * packaging is <tt>jpi</tt>.
     */
    public void recipeLoadCurrentPlugin() throws Exception {
    	final Enumeration<URL> jpls = getClass().getClassLoader().getResources("the.jpl");
        final Enumeration<URL> hpls = getClass().getClassLoader().getResources("the.hpl");

        final List<URL> all = Collections.list(jpls);
        all.addAll(Collections.list(hpls));
        
        if(all.isEmpty())    return; // nope
        
        recipes.add(new JenkinsRecipe.Runner() {
            @Override
            public void decorateHome(JenkinsRule testCase, File home) throws Exception {
                decorateHomeFor(home, all);
            }
        });
    }

    static void decorateHomeFor(File home, List<URL> all) throws Exception {
        List<Jpl> jpls = new ArrayList<Jpl>();
        for (URL hpl : all) {
            Jpl jpl = new Jpl(home, hpl);
            jpl.loadManifest();
            jpls.add(jpl);
        }
        for (Jpl jpl : jpls) {
            jpl.resolveDependencies(jpls);
        }
    }

    private static final class Jpl {
        private final File home;
                final URL jpl;
                Manifest m;
                private String shortName;

                Jpl(File home, URL jpl) {
                    this.home = home;
                    this.jpl = jpl;
                }

                void loadManifest() throws IOException {
                    m = new Manifest(jpl.openStream());
                    shortName = m.getMainAttributes().getValue("Short-Name");
                    if(shortName ==null)
                        throw new Error(jpl +" doesn't have the Short-Name attribute");
                    FileUtils.copyURLToFile(jpl, new File(home, "plugins/" + shortName + ".jpl"));
                }

                void resolveDependencies(List<Jpl> jpls) throws Exception {
                    // make dependency plugins available
                    // TODO: probably better to read POM, but where to read from?
                    // TODO: this doesn't handle transitive dependencies

                    // Tom: plugins are now searched on the classpath first. They should be available on
                    // the compile or test classpath.
                    // For transitive dependencies, we could evaluate Plugin-Dependencies transitively.
                    String dependencies = m.getMainAttributes().getValue("Plugin-Dependencies");
                    if(dependencies!=null) {
                        DEPENDENCY:
                        for( String dep : dependencies.split(",")) {
                            String suffix = ";resolution:=optional";
                            boolean optional = dep.endsWith(suffix);
                            if (optional) {
                                dep = dep.substring(0, dep.length() - suffix.length());
                            }
                            String[] tokens = dep.split(":");
                            String artifactId = tokens[0];
                            String version = tokens[1];

                            for (Jpl other : jpls) {
                                if (other.shortName.equals(artifactId))
                                    continue DEPENDENCY;    // resolved from another JPL file
                            }

                            File dependencyJar=resolveDependencyJar(artifactId,version);
                            if (dependencyJar == null) {
                                if (optional) {
                                    LOGGER.log(Level.INFO, "cannot resolve optional dependency {0} of {1}; skipping", new Object[] {dep, shortName});
                                    continue;
                                }
                                throw new IOException("Could not resolve " + dep + " in " + System.getProperty("java.class.path"));
                            }

                            File dst = new File(home, "plugins/" + artifactId + ".jpi");
                            if(!dst.exists() || dst.lastModified()!=dependencyJar.lastModified()) {
                                try {
                                    FileUtils.copyFile(dependencyJar, dst);
                                } catch (ClosedByInterruptException x) {
                                    throw new AssumptionViolatedException("copying dependencies was interrupted", x);
                                }
                            }
                        }
                    }
                }

            private @CheckForNull File resolveDependencyJar(String artifactId, String version) throws Exception {
                // try to locate it from manifest
                Enumeration<URL> manifests = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
                while (manifests.hasMoreElements()) {
                    URL manifest = manifests.nextElement();
                    InputStream is = manifest.openStream();
                    Manifest m = new Manifest(is);
                    is.close();

                    if (artifactId.equals(m.getMainAttributes().getValue("Short-Name")))
                        return Which.jarFile(manifest);
                }

                // For snapshot plugin dependencies, an IDE may have replaced ~/.m2/repository/…/${artifactId}.hpi with …/${artifactId}-plugin/target/classes/
                // which unfortunately lacks META-INF/MANIFEST.MF so try to find index.jelly (which every plugin should include) and thus the ${artifactId}.hpi:
                Enumeration<URL> jellies = getClass().getClassLoader().getResources("index.jelly");
                while (jellies.hasMoreElements()) {
                    URL jellyU = jellies.nextElement();
                    if (jellyU.getProtocol().equals("file")) {
                        File jellyF = new File(jellyU.toURI());
                        File classes = jellyF.getParentFile();
                        if (classes.getName().equals("classes")) {
                            File target = classes.getParentFile();
                            if (target.getName().equals("target")) {
                                File hpi = new File(target, artifactId + ".hpi");
                                if (hpi.isFile()) {
                                    return hpi;
                                }
                            }
                        }
                    }
                }

                return null;
            }
            
    }

    public JenkinsRule withNewHome() {
        return with(HudsonHomeLoader.NEW);
    }

    public JenkinsRule withExistingHome(File source) throws Exception {
        return with(new HudsonHomeLoader.CopyExisting(source));
    }

    /**
     * Declares that this test case expects to start with one of the preset data sets.
     * See {@code test/src/main/preset-data/}
     * for available datasets and what they mean.
     */
    public JenkinsRule withPresetData(String name) {
        name = "/" + name + ".zip";
        URL res = getClass().getResource(name);
        if(res==null)   throw new IllegalArgumentException("No such data set found: "+name);

        return with(new HudsonHomeLoader.CopyExisting(res));
    }

    public JenkinsRule with(HudsonHomeLoader homeLoader) {
        this.homeLoader = homeLoader;
        return this;
    }


    /**
     * Executes the given closure on the server, by the servlet request handling thread,
     * in the context of an HTTP request.
     *
     * <p>
     * In {@link JenkinsRule}, a thread that's executing the test code is different from the thread
     * that carries out HTTP requests made through {@link WebClient}. But sometimes you want to
     * make assertions and other calls with side-effect from within the request handling thread.
     *
     * <p>
     * This method allows you to do just that. It is useful for testing some methods that
     * require {@link org.kohsuke.stapler.StaplerRequest} and {@link org.kohsuke.stapler.StaplerResponse}, or getting the credential
     * of the current user (via {@link jenkins.model.Jenkins#getAuthentication()}, and so on.
     *
     * @param c
     *      The closure to be executed on the server.
     * @return
     *      The return value from the closure.
     * @throws Exception
     *      If a closure throws any exception, that exception will be carried forward.
     */
    public <V> V executeOnServer(Callable<V> c) throws Exception {
        return createWebClient().executeOnServer(c);
    }

    /**
     * Sometimes a part of a test case may ends up creeping into the serialization tree of {@link hudson.model.Saveable#save()},
     * so detect that and flag that as an error.
     */
    private Object writeReplace() {
        throw new AssertionError("JenkinsRule " + testDescription.getDisplayName() + " is not supposed to be serialized");
    }

    /**
     * This is to assist Groovy test clients who are incapable of instantiating the inner classes properly.
     */
    public WebClient createWebClient() {
        return new WebClient();
    }

    /**
     * Extends {@link com.gargoylesoftware.htmlunit.WebClient} and provide convenience methods
     * for accessing Hudson.
     */
    public class WebClient extends com.gargoylesoftware.htmlunit.WebClient {
        private static final long serialVersionUID = -7944895389154288881L;

        private List<WebResponseListener> webResponseListeners = new ArrayList<WebResponseListener>();

        public WebClient() {
            super(BrowserVersion.BEST_SUPPORTED);

//            setJavaScriptEnabled(false);
            setPageCreator(HudsonPageCreator.INSTANCE);
            clients.add(this);
            // make ajax calls run as post-action for predictable behaviors that simplify debugging
            setAjaxController(new AjaxController() {
                private static final long serialVersionUID = -76034615893907856L;
                public boolean processSynchron(HtmlPage page, WebRequest settings, boolean async) {
                    return false;
                }
            });

            setCssErrorHandler(new CSSErrorHandler() {
                final CSSErrorHandler defaultHandler = new DefaultCssErrorHandler();

                @Override
                public void warning(final CSSParseException exception) throws com.gargoylesoftware.css.parser.CSSException {
                    if (!ignore(exception))
                        defaultHandler.warning(exception);
                }

                @Override
                public void error(final CSSParseException exception) throws CSSException {
                    if (!ignore(exception))
                        defaultHandler.error(exception);
                }

                @Override
                public void fatalError(final CSSParseException exception) throws CSSException {
                    if (!ignore(exception))
                        defaultHandler.fatalError(exception);
                }

                private boolean ignore(final CSSParseException exception) {
                    String uri = exception.getURI();
                    return uri.contains("/yui/")
                        // TODO JENKINS-14749: these are a mess today, and we know that
                        || uri.contains("/css/style.css") || uri.contains("/css/responsive-grid.css");
                }
            });

            // if no other debugger is installed, install jsDebugger,
            // so as not to interfere with the 'Dim' class.
            AbstractJavaScriptEngine<?> javaScriptEngine = getJavaScriptEngine();
            if (javaScriptEngine instanceof JavaScriptEngine) {
                ((JavaScriptEngine) javaScriptEngine).getContextFactory()
                        .addListener(new ContextFactory.Listener() {
                            public void contextCreated(Context cx) {
                                if (cx.getDebugger() == null)
                                    cx.setDebugger(jsDebugger, null);
                            }

                            public void contextReleased(Context cx) {
                            }
                        });
            }

            // avoid a hang by setting a time out. It should be long enough to prevent
            // false-positive timeout on slow systems
            //setTimeout(60*1000);
        }


        public void addWebResponseListener(WebResponseListener listener) {
            webResponseListeners.add(listener);
        }

        @Override
        public WebResponse loadWebResponse(final WebRequest webRequest) throws IOException {
            WebResponse webResponse = super.loadWebResponse(webRequest);
            if (!webResponseListeners.isEmpty()) {
                for (WebResponseListener listener : webResponseListeners) {
                    listener.onLoadWebResponse(webRequest, webResponse);
                }
            }
            return webResponse;
        }

        /**
         * Logs in to Jenkins.
         */
        public WebClient login(String username, String password) throws Exception {
            return login(username,password,false);
        }

        /**
         * Returns {@code true} if JavaScript is enabled and the script engine was loaded successfully.
         * Short-hand method to ease discovery of feature + improve readability
         *
         * @return {@code true} if JavaScript is enabled
         * @see WebClientOptions#isJavaScriptEnabled()
         * @since 2.0
         */
        public boolean isJavaScriptEnabled() {
            return getOptions().isJavaScriptEnabled();
        }

        /**
         * Enables/disables JavaScript support.
         * Short-hand method to ease discovery of feature + improve readability
         *
         * @param enabled {@code true} to enable JavaScript support
         * @see WebClientOptions#setJavaScriptEnabled(boolean)
         * @since 2.0
         */
        public void setJavaScriptEnabled(boolean enabled) {
            getOptions().setJavaScriptEnabled(enabled);
        }

        /**
         * Enables/disables JavaScript support.
         * Fluent method to ease discovery of feature + improve readability
         *
         * @param enabled {@code true} to enable JavaScript support
         * @return self for fluent method chaining
         * @see WebClientOptions#setJavaScriptEnabled(boolean)
         * @since 2.42
         */
        public WebClient withJavaScriptEnabled(boolean enabled) {
            setJavaScriptEnabled(enabled);
            return this;
        }
    
        /**
         * Returns true if an exception will be thrown in the event of a failing response code.
         * Short-hand method to ease discovery of feature + improve readability
         * 
         * @return {@code true} if an exception will be thrown in the event of a failing response code
         * @see WebClientOptions#isThrowExceptionOnFailingStatusCode()
         * @since 2.42
         */
        public boolean isThrowExceptionOnFailingStatusCode() {
            return getOptions().isThrowExceptionOnFailingStatusCode();
        }

        /**
         * Changes the behavior of this webclient when a script error occurs.
         * Short-hand method to ease discovery of feature + improve readability
         *
         * @param enabled {@code true} to enable this feature
         * @see WebClientOptions#setThrowExceptionOnFailingStatusCode(boolean)
         * @since 2.42
         */
        public void setThrowExceptionOnFailingStatusCode(boolean enabled) {
            getOptions().setThrowExceptionOnFailingStatusCode(enabled);
        }

        /**
         * Changes the behavior of this webclient when a script error occurs.
         * Fluent method to ease discovery of feature + improve readability
         * 
         * @param enabled {@code true} to enable this feature
         * @return self for fluent method chaining
         * @see WebClientOptions#setThrowExceptionOnFailingStatusCode(boolean)
         * @since 2.42
         */
        public WebClient withThrowExceptionOnFailingStatusCode(boolean enabled) {
            setThrowExceptionOnFailingStatusCode(enabled);
            return this;
        }
        
        /**
         * Returns whether or not redirections will be followed automatically on receipt of a redirect status code from the server.
         * Short-hand method to ease discovery of feature + improve readability
         * 
         * @return {@code true} if automatic redirection is enabled
         * @see WebClientOptions#isRedirectEnabled()
         * @since 2.42
         */
        public boolean isRedirectEnabled() {
            return getOptions().isRedirectEnabled();
        }

        /**
         * Sets whether or not redirections will be followed automatically on receipt of a redirect status code from the server.
         * Short-hand method to ease discovery of feature + improve readability
         * 
         * @param enabled {@code true} to enable automatic redirection
         * @see com.gargoylesoftware.htmlunit.WebClientOptions#setRedirectEnabled(boolean)
         * @since 2.42
         */
        public void setRedirectEnabled(boolean enabled) {
            getOptions().setRedirectEnabled(enabled);
        }

        /**
         * Sets whether or not redirections will be followed automatically on receipt of a redirect status code from the server.
         * Fluent method to ease discovery of feature + improve readability
         *
         * @param enabled {@code true} to enable automatic redirection
         * @return self for fluent method chaining
         * @see com.gargoylesoftware.htmlunit.WebClientOptions#setRedirectEnabled(boolean)
         * @since 2.42
         */
        public WebClient withRedirectEnabled(boolean enabled) {
            setRedirectEnabled(enabled);
            return this;
        }

        /**
         * Logs in to Jenkins.
         */
        public WebClient login(String username, String password, boolean rememberMe) throws Exception {
            HtmlPage page = goTo("login");
//            page = (HtmlPage) page.getFirstAnchorByText("Login").click();

            HtmlForm form = page.getFormByName("login");
            form.getInputByName("j_username").setValueAttribute(username);
            form.getInputByName("j_password").setValueAttribute(password);
            try {
                form.getInputByName("remember_me").setChecked(rememberMe);
            } catch (ElementNotFoundException e) {
                // remember me not available is OK so long as the caller didn't ask for it
                assert !rememberMe;
            }
            HtmlFormUtil.submit(form, null);
            return this;
        }

        /**
         * Logs in to Hudson, by using the user name as the password.
         *
         * <p>
         * See {@link #configureUserRealm} for how the container is set up with the user names
         * and passwords. All the test accounts have the same user name and password.
         */
        public WebClient login(String username) throws Exception {
            login(username, username);
            return this;
        }

        /**
         * Executes the given closure on the server, by the servlet request handling thread,
         * in the context of an HTTP request.
         *
         * <p>
         * In {@link JenkinsRule}, a thread that's executing the test code is different from the thread
         * that carries out HTTP requests made through {@link WebClient}. But sometimes you want to
         * make assertions and other calls with side-effect from within the request handling thread.
         *
         * <p>
         * This method allows you to do just that. It is useful for testing some methods that
         * require {@link org.kohsuke.stapler.StaplerRequest} and {@link org.kohsuke.stapler.StaplerResponse}, or getting the credential
         * of the current user (via {@link jenkins.model.Jenkins#getAuthentication()}, and so on.
         *
         * @param c
         *      The closure to be executed on the server.
         * @return
         *      The return value from the closure.
         * @throws Exception
         *      If a closure throws any exception, that exception will be carried forward.
         */
        public <V> V executeOnServer(final Callable<V> c) throws Exception {
            final Exception[] t = new Exception[1];
            final List<V> r = new ArrayList<V>(1);  // size 1 list

            ClosureExecuterAction cea = jenkins.getExtensionList(RootAction.class).get(ClosureExecuterAction.class);
            UUID id = UUID.randomUUID();
            cea.add(id,new Runnable() {
                public void run() {
                    try {
                        StaplerResponse rsp = Stapler.getCurrentResponse();
                        rsp.setStatus(200);
                        rsp.setContentType("text/html");
                        r.add(c.call());
                    } catch (Exception e) {
                        t[0] = e;
                    }
                }
            });
            goTo("closures/?uuid="+id);

            if (t[0]!=null)
                throw t[0];
            return r.get(0);
        }

        public HtmlPage search(String q) throws IOException, SAXException {
            HtmlPage top = goTo("");
            HtmlForm search = top.getFormByName("search");
            search.getInputByName("q").setValueAttribute(q);
            return (HtmlPage)HtmlFormUtil.submit(search, null);
        }

        /**
         * Short for {@code getPage(r,"")}, to access the top page of a build.
         */
        public HtmlPage getPage(Run r) throws IOException, SAXException {
            return getPage(r,"");
        }

        /**
         * Accesses a page inside {@link Run}.
         *
         * @param relative
         *      Relative URL within the build URL, like "changes". Doesn't start with '/'. Can be empty.
         */
        public HtmlPage getPage(Run r, String relative) throws IOException, SAXException {
            return goTo(r.getUrl()+relative);
        }

        public HtmlPage getPage(Item item) throws IOException, SAXException {
            return getPage(item,"");
        }

        public HtmlPage getPage(Item item, String relative) throws IOException, SAXException {
            return goTo(item.getUrl()+relative);
        }

        public HtmlPage getPage(Node item) throws IOException, SAXException {
            return getPage(item, "");
        }

        public HtmlPage getPage(Node item, String relative) throws IOException, SAXException {
            return goTo(item.toComputer().getUrl()+relative);
        }

        public HtmlPage getPage(View view) throws IOException, SAXException {
            return goTo(view.getUrl());
        }

        public HtmlPage getPage(View view, String relative) throws IOException, SAXException {
            return goTo(view.getUrl()+relative);
        }

        /**
         * @deprecated
         *      This method expects a full URL. This method is marked as deprecated to warn you
         *      that you probably should be using {@link #goTo(String)} method, which accepts
         *      a relative path within the Hudson being tested. (IOW, if you really need to hit
         *      a website on the internet, there's nothing wrong with using this method.)
         */
        @SuppressWarnings("unchecked")
        @Override
        public Page getPage(String url) throws IOException, FailingHttpStatusCodeException {
            try {
                return super.getPage(url);
            } finally {
                WebClientUtil.waitForJSExec(this);
            }
        }

        /**
         * Requests an HTML page within Jenkins.
         *
         * @param relative
         *      Relative path within Jenkins. Starts without '/'.
         *      For example, "job/test/" to go to a job top page.
         */
        public HtmlPage goTo(String relative) throws IOException, SAXException {
            Page p = goTo(relative, "text/html");
            if (p instanceof HtmlPage) {
                return (HtmlPage) p;
            } else {
                throw new AssertionError("Expected text/html but instead the content type was "+p.getWebResponse().getContentType());
            }
        }

        /**
         * Requests a page within Jenkins.
         *
         * @param relative
         *      Relative path within Jenkins. Starts without '/'.
         *      For example, "job/test/" to go to a job top page.
         * @param expectedContentType the expected {@link WebResponse#getContentType}, or null to do no such check
         */
        public Page goTo(String relative, @CheckForNull String expectedContentType) throws IOException, SAXException {
            assert !relative.startsWith("/");
            Page p;
            try {
                p = super.getPage(getContextPath() + relative);
                WebClientUtil.waitForJSExec(this);
            } catch (IOException x) {
                Throwable cause = x.getCause();
                if (cause instanceof SocketTimeoutException) {
                    throw new AssumptionViolatedException("failed to get " + relative + " due to read timeout", cause);
                } else if (cause != null) {
                    cause.printStackTrace(); // SUREFIRE-1067 workaround
                }
                throw x;
            }
            if (expectedContentType != null) {
                assertThat(p.getWebResponse().getContentType(), is(expectedContentType));
            }
            return p;
        }

        /** Loads a page as XML. Useful for testing Jenkins's XML API, in concert with
         * assertXPath(DomNode page, String xpath)
         * @param path   the path part of the url to visit
         * @return  the XmlPage found at that url
         * @throws IOException
         * @throws SAXException
         */
        public XmlPage goToXml(String path) throws IOException, SAXException {
            Page page = goTo(path, "application/xml");
            if (page instanceof XmlPage)
                return (XmlPage) page;
            else
                return null;
        }

        /**
         * Verify that the server rejects an attempt to load the given page.
         * @param url a URL path (relative to Jenkins root)
         * @param statusCode the expected failure code (such as {@link HttpURLConnection#HTTP_FORBIDDEN})
         * @since 1.504
         */
        public void assertFails(String url, int statusCode) throws Exception {
            assert !url.startsWith("/");
            boolean currentConfiguration = isThrowExceptionOnFailingStatusCode();
            // enforce the throwing of exception for the catch scope only
            setThrowExceptionOnFailingStatusCode(true);
            try {
                fail(url + " should have been rejected but produced: " + super.getPage(getContextPath() + url).getWebResponse().getContentAsString());
            } catch (FailingHttpStatusCodeException x) {
                assertEquals(statusCode, x.getStatusCode());
            } finally {
                setThrowExceptionOnFailingStatusCode(currentConfiguration);
            }
        }

        /**
         * Returns the URL of the webapp top page.
         * URL ends with '/'.
         * <p>This is actually the same as {@link #getURL} and should not be confused with {@link #contextPath}.
         */
        public String getContextPath() throws IOException {
            return getURL().toExternalForm();
        }

        /**
         * Adds a security crumb to the request.
         * Use {@link #createCrumbedUrl} instead if you intend to call {@link WebRequest#setRequestBody}, typical of a POST request.
         */
        public WebRequest addCrumb(WebRequest req) {
            ArrayList<NameValuePair> params = new ArrayList<>();
            params.add(getCrumbHeaderNVP());
            List<NameValuePair> oldParams = req.getRequestParameters();
            if (oldParams != null) {
                params.addAll(oldParams);
            }
            req.setRequestParameters(params);
            return req;
        }

        /**
         * Creates a URL with crumb parameters relative to {{@link #getContextPath()}
         */
        public URL createCrumbedUrl(String relativePath) throws IOException {
            CrumbIssuer issuer = jenkins.getCrumbIssuer();
            String crumbName = issuer.getDescriptor().getCrumbRequestField();
            String crumb = issuer.getCrumb(null);
            if (relativePath.indexOf('?') == -1) {
                return new URL(getContextPath()+relativePath+"?"+crumbName+"="+crumb);
            }
            return new URL(getContextPath()+relativePath+"&"+crumbName+"="+crumb);
        }

        /**
         * Add the "Authorization" header with Basic credentials derived from login and password using Base64
         * @since 2.32
         */
        public @Nonnull WebClient withBasicCredentials(@Nonnull String login, @Nonnull String passwordOrToken) {
            String authCode = new String(Base64.getEncoder().encode((login + ":" + passwordOrToken).getBytes(StandardCharsets.UTF_8)));

            addRequestHeader(HttpHeaders.AUTHORIZATION, "Basic " + authCode);
            return this;
        }

        /**
         * Use {@code loginAndPassword} as login AND password, especially useful for {@link DummySecurityRealm}
         * Add the "Authorization" header with Basic credentials derived from login using Base64
         * @since 2.32
         */
        public @Nonnull WebClient withBasicCredentials(@Nonnull String loginAndPassword){
            return withBasicCredentials(loginAndPassword, loginAndPassword);
        }

        /**
         * Retrieve the {@link ApiTokenProperty} from the user, derive credentials from it and place it in Basic authorization header
         * @see #withBasicCredentials(String, String)
         * @since 2.32
         */
        public @Nonnull WebClient withBasicApiToken(@Nonnull User user){
            return withBasicCredentials(user.getId(), user.getProperty(ApiTokenProperty.class).getApiToken());
        }

        /**
         * Retrieve the {@link ApiTokenProperty} from the associated user, derive credentials from it and place it in Basic authorization header
         * @see #withBasicApiToken(User)
         * @since 2.32
         */
        public @Nonnull WebClient withBasicApiToken(@Nonnull String userId){
            User user = User.getById(userId, false);
            assertNotNull("The userId must correspond to an already created User", user);
            return withBasicApiToken(user);
        }

        /**
         * Makes an HTTP request, process it with the given request handler, and returns the response.
         */
        public HtmlPage eval(final Runnable requestHandler) throws IOException, SAXException {
            ClosureExecuterAction cea = jenkins.getExtensionList(RootAction.class).get(ClosureExecuterAction.class);
            UUID id = UUID.randomUUID();
            cea.add(id,requestHandler);
            return goTo("closures/?uuid="+id);
        }

        /**
         * Starts an interactive JavaScript debugger, and break at the next JavaScript execution.
         *
         * <p>
         * This is useful during debugging a test so that you can step execute and inspect state of JavaScript.
         * This will launch a Swing GUI, and the method returns immediately.
         *
         * <p>
         * Note that installing a debugger appears to make an execution of JavaScript substantially slower.
         *
         * <p>
         * TODO: because each script block evaluation in HtmlUnit is done in a separate Rhino context,
         * if you step over from one script block, the debugger fails to kick in on the beginning of the next script
         * block.
         * This makes it difficult to set a break point on arbitrary script block in the HTML page. We need to fix this
         * by tweaking {@link org.mozilla.javascript.tools.debugger.Dim.StackFrame#onLineChange(Context, int)}.
         */
        public Dim interactiveJavaScriptDebugger() {
            Global global = new Global();
            HtmlUnitContextFactory cf = ((JavaScriptEngine)getJavaScriptEngine()).getContextFactory();
            global.init(cf);

            Dim dim = org.mozilla.javascript.tools.debugger.Main.mainEmbedded(cf, global, "Rhino debugger: " + testDescription.getDisplayName());

            // break on exceptions. this catch most of the errors
            dim.setBreakOnExceptions(true);

            return dim;
        }
    }

    // needs to keep reference, or it gets GC-ed.
    private static final Logger XML_HTTP_REQUEST_LOGGER = Logger.getLogger(XMLHttpRequest.class.getName());
    private static final Logger SPRING_LOGGER = Logger.getLogger("org.springframework");
    private static final Logger JETTY_LOGGER = Logger.getLogger("org.mortbay.log");
    private static final Logger HTMLUNIT_DOCUMENT_LOGGER = Logger.getLogger("com.gargoylesoftware.htmlunit.javascript.host.Document");
    private static final Logger HTMLUNIT_JS_LOGGER = Logger.getLogger("com.gargoylesoftware.htmlunit.javascript.StrictErrorReporter");

    static {
        // screen scraping relies on locale being fixed.
        Locale.setDefault(Locale.ENGLISH);

        {// enable debug assistance, since tests are often run from IDE
            Dispatcher.TRACE = true;
            MetaClass.NO_CACHE=true;
            // load resources from the source dir.
            File dir = new File("src/main/resources");
            if(dir.exists() && MetaClassLoader.debugLoader==null)
                try {
                    MetaClassLoader.debugLoader = new MetaClassLoader(
                        new URLClassLoader(new URL[]{dir.toURI().toURL()}));
                } catch (MalformedURLException e) {
                    throw new AssertionError(e);
                }
        }

        // suppress some logging which we do not much care about here
        SPRING_LOGGER.setLevel(Level.WARNING);
        JETTY_LOGGER.setLevel(Level.WARNING);

        // hudson-behavior.js relies on this to decide whether it's running unit tests.
        Main.isUnitTest = true;

        // prototype.js calls this method all the time, so ignore this warning.
        XML_HTTP_REQUEST_LOGGER.setFilter(new Filter() {
            public boolean isLoggable(LogRecord record) {
                return !record.getMessage().contains("XMLHttpRequest.getResponseHeader() was called before the response was available.");
            }
        });
        // JENKINS-14749: prototype.js intentionally swallows this exception (thrown on Firefox which we simulate), but HtmlUnit still tries to log it.
        HTMLUNIT_DOCUMENT_LOGGER.setFilter(new Filter() {
            @Override public boolean isLoggable(LogRecord record) {
                return !record.getMessage().equals("Unexpected exception occurred while parsing HTML snippet");
            }
        });
        HTMLUNIT_JS_LOGGER.setFilter(new Filter() {
            @Override public boolean isLoggable(LogRecord record) {
                return !record.getMessage().contains("Unexpected exception occurred while parsing HTML snippet: input name=\"x\"");
            }
        });

        // remove the upper bound of the POST data size in Jetty.
        System.setProperty("org.mortbay.jetty.Request.maxFormContentSize","-1");
    }

    private static final Logger LOGGER = Logger.getLogger(HudsonTestCase.class.getName());

    public static final List<ToolProperty<?>> NO_PROPERTIES = Collections.emptyList();

    /**
     * Specify this to a TCP/IP port number to have slaves started with the debugger.
     */
    public static final int SLAVE_DEBUG_PORT = Integer.getInteger(HudsonTestCase.class.getName()+".slaveDebugPort",-1);

    public static final MimeTypes MIME_TYPES;
    static {
        jettyLevel(Level.WARNING); // suppress Log.initialize message
        try {
            MIME_TYPES = new MimeTypes();
        } finally {
            jettyLevel(Level.INFO);
        }
        MIME_TYPES.addMimeMapping("js","application/javascript");
        Functions.DEBUG_YUI = true;

        // during the unit test, predictably releasing classloader is important to avoid
        // file descriptor leak.
        ClassicPluginStrategy.useAntClassLoader = true;

        // DNS multicast support takes up a lot of time during tests, so just disable it altogether
        // this also prevents tests from falsely advertising Hudson
        DNSMultiCast.disabled = true;

        try {
            GNUCLibrary.LIBC.unsetenv("MAVEN_OPTS");
            GNUCLibrary.LIBC.unsetenv("MAVEN_DEBUG_OPTS");
        } catch (LinkageError x) {
            // skip; TODO 1.630+ can use Functions.isGlibcSupported
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,"Failed to cancel out MAVEN_OPTS",e);
        }
    }

    public static class TestBuildWrapper extends BuildWrapper {
        public Result buildResultInTearDown;

        @Override
        public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            return new BuildWrapper.Environment() {
                @Override
                public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                    buildResultInTearDown = build.getResult();
                    return true;
                }
            };
        }

        @Extension
        public static class TestBuildWrapperDescriptor extends BuildWrapperDescriptor {
            @Override
            public boolean isApplicable(AbstractProject<?, ?> project) {
                return true;
            }

            @Override
            public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getDisplayName() {
                return "TestBuildWrapper";
            }
        }
    }

    public Description getTestDescription() {
        return testDescription;
    }

    private NameValuePair getCrumbHeaderNVP() {
        return new NameValuePair(jenkins.getCrumbIssuer().getDescriptor().getCrumbRequestField(),
                        jenkins.getCrumbIssuer().getCrumb( null ));
    }
}
