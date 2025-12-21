/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.util.ProcessTree;
import hudson.util.StreamCopyThread;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

/**
 * Manages inbound agents.
 * While these run on the local host, they are launched outside of Jenkins.
 * Usage: <pre>{@code
 * private static final InboundAgentFixture FIXTURE = InboundAgentFixture.newBuilder().build();
 * }</pre>
 *
 * <p>To avoid flakiness when tearing down the test, ensure that the agent has gone offline with:
 *
 * <pre>{@code
 * Slave agent = inboundAgents.createAgent(r, […]);
 * try {
 *     […]
 * } finally {
 *     inboundAgents.stop(r, agent.getNodeName());
 * }}</pre>
 *
 * @see org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension
 * @see org.jvnet.hudson.test.InboundAgentRule
 * @see JenkinsRule#createComputerLauncher
 * @see JenkinsRule#createSlave()
 */
public class InboundAgentFixture {

    private static final Logger LOGGER = Logger.getLogger(InboundAgentFixture.class.getName());

    private final String id = UUID.randomUUID().toString();
    private final Map<String, List<Process>> procs = Collections.synchronizedMap(new HashMap<>());

    private final Set<String> workDirs = Collections.synchronizedSet(new HashSet<>());
    private final Set<File> jars = Collections.synchronizedSet(new HashSet<>());

    /**
     * The options used to (re)start an inbound agent.
     */
    @SuppressWarnings({"unused", "unchecked", "rawtypes"})
    public static class Options<O extends Options> implements Serializable {

        @CheckForNull
        protected String name;

        protected boolean webSocket;

        @CheckForNull
        protected String tunnel;

        protected List<String> javaOptions = new ArrayList<>();
        protected boolean start = true;
        protected Map<String, Level> loggers = new LinkedHashMap<>();
        protected String label;
        protected PrefixedOutputStream.Builder prefixedOutputStreamBuilder = PrefixedOutputStream.builder();
        protected String trustStorePath;
        protected String trustStorePassword;
        protected String cert;
        protected boolean noCertificateCheck;

        protected Options() {}

        @CheckForNull
        public String getName() {
            return name;
        }

        public void setName(@CheckForNull String name) {
            this.name = name;
        }

        public boolean isWebSocket() {
            return webSocket;
        }

        public void setWebSocket(boolean webSocket) {
            this.webSocket = webSocket;
        }

        @CheckForNull
        public String getTunnel() {
            return tunnel;
        }

        public void setTunnel(@CheckForNull String tunnel) {
            this.tunnel = tunnel;
        }

        public List<String> getJavaOptions() {
            return javaOptions;
        }

        public void setJavaOptions(List<String> javaOptions) {
            this.javaOptions = javaOptions;
        }

        public boolean isStart() {
            return start;
        }

        public void setStart(boolean start) {
            this.start = start;
        }

        public Map<String, Level> getLoggers() {
            return loggers;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public PrefixedOutputStream.Builder getPrefixedOutputStreamBuilder() {
            return prefixedOutputStreamBuilder;
        }

        public String getTrustStorePath() {
            return trustStorePath;
        }

        public void setTrustStorePath(String trustStorePath) {
            this.trustStorePath = trustStorePath;
        }

        public String getTrustStorePassword() {
            return trustStorePassword;
        }

        public void setTrustStorePassword(String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
        }

        public String getCert() {
            return cert;
        }

        public void setCert(String cert) {
            this.cert = cert;
        }

        public boolean isNoCertificateCheck() {
            return noCertificateCheck;
        }

        public void setNoCertificateCheck(boolean noCertificateCheck) {
            this.noCertificateCheck = noCertificateCheck;
        }

        /**
         * Compute java options required to connect to the underlying instance.
         * If {@link #cert} or {@link #noCertificateCheck} is set, trustStore options are not computed.
         * This prevents Remoting from implicitly bypassing failures related to {@code -cert} or {@code -noCertificateCheck}.
         *
         * @param defaultJavaOptions The instance java options
         */
        public void computeJavaOptions(List<String> defaultJavaOptions) {
            if (cert != null || noCertificateCheck) {
                return;
            }
            if (trustStorePath != null && trustStorePassword != null) {
                javaOptions.addAll(List.of(
                        "-Djavax.net.ssl.trustStore=" + trustStorePath,
                        "-Djavax.net.ssl.trustStorePassword=" + trustStorePassword));
            } else {
                javaOptions.addAll(defaultJavaOptions);
            }
        }

        /**
         * A builder of {@link Options}.
         *
         * <p>Instances of {@link Builder} are created by calling {@link
         * InboundAgentFixture.Options#newBuilder}.
         */
        public static class Builder<B extends Builder, O extends Options> {

            protected final Options<O> options = new Options<>();

            /**
             * Set the name of the agent.
             *
             * @param name the name
             * @return this builder
             */
            public B name(String name) {
                options.name = name;
                return (B) this;
            }

            /**
             * Set a color for agent logs.
             *
             * @param color the color
             * @return this builder
             */
            public B color(PrefixedOutputStream.AnsiColor color) {
                options.prefixedOutputStreamBuilder.withColor(color);
                return (B) this;
            }

            /**
             * Use WebSocket when connecting.
             *
             * @return this builder
             */
            public B webSocket() {
                return webSocket(true);
            }

            /**
             * Configure usage of WebSocket when connecting.
             *
             * @param websocket use websocket if true, otherwise use inbound TCP
             * @return this builder
             */
            public B webSocket(boolean websocket) {
                options.webSocket = websocket;
                return (B) this;
            }

            /**
             * Set a tunnel for the agent
             *
             * @return this builder
             */
            public B tunnel(String tunnel) {
                options.tunnel = tunnel;
                return (B) this;
            }

            public B javaOptions(String... opts) {
                options.javaOptions.addAll(List.of(opts));
                return (B) this;
            }

            /**
             * Provide a custom truststore for the agent JVM. Can be useful when using a setup with a reverse proxy.
             *
             * @param path     the path to the truststore
             * @param password the password for the truststore
             * @return this builder
             */
            public B trustStore(String path, String password) {
                options.trustStorePath = path;
                options.trustStorePassword = password;
                return (B) this;
            }

            /**
             * Sets a custom certificate for the agent JVM, passed as the Remoting `-cert` CLI argument.
             * When using {@code RealJenkinsExtension}, use {@link RealJenkinsExtension#getRootCAPem()} to obtain the required value to pass to this method.
             *
             * @param cert the certificate to use
             * @return this builder
             */
            public B cert(String cert) {
                options.cert = cert;
                return (B) this;
            }

            /**
             * Disables certificate verification for the agent JVM, passed as the Remoting `-noCertificateCheck` CLI argument.
             *
             * @return this builder
             */
            public B noCertificateCheck() {
                options.noCertificateCheck = true;
                return (B) this;
            }

            /**
             * Skip starting the agent.
             *
             * @return this builder
             */
            public B skipStart() {
                options.start = false;
                return (B) this;
            }

            /**
             * Set a label for the agent.
             *
             * @return this builder.
             */
            public B label(String label) {
                options.label = label;
                return (B) this;
            }

            public B withLogger(Class<?> clazz, Level level) {
                return withLogger(clazz.getName(), level);
            }

            public B withPackageLogger(Class<?> clazz, Level level) {
                return withLogger(clazz.getPackageName(), level);
            }

            public B withLogger(String logger, Level level) {
                options.loggers.put(logger, level);
                return (B) this;
            }

            /**
             * Build and return an {@link Options}.
             *
             * @return a new {@link Options}
             */
            public O build() {
                return (O) options;
            }
        }

        public static <T extends Builder> T newBuilder() {
            return (T) new Builder();
        }
    }

    public Set<String> getWorkDirs() {
        return workDirs;
    }

    public Set<File> getJars() {
        return jars;
    }

    /**
     * Creates, attaches, and starts a new inbound agent.
     *
     * @param name an optional {@link Slave#getNodeName}
     */
    public Slave createAgent(@NonNull JenkinsRule r, @CheckForNull String name) throws Exception {
        return createAgent(r, Options.newBuilder().name(name).build());
    }

    /**
     * Creates, attaches, and optionally starts a new inbound agent.
     *
     * @param options the options
     */
    public Slave createAgent(@NonNull JenkinsRule r, Options options) throws Exception {
        Slave s = createAgentJR(r, options);
        workDirs.add(s.getRemoteFS());
        if (options.isStart()) {
            start(r, options);
        }
        return s;
    }

    /**
     * (Re-)starts an existing inbound agent.
     */
    public void start(@NonNull JenkinsRule r, @NonNull String name) throws Exception {
        start(r, Options.newBuilder().name(name).build());
    }

    /**
     * (Re-)starts an existing inbound agent.
     */
    public void start(@NonNull JenkinsRule r, Options options) throws Exception {
        String name = options.getName();
        Objects.requireNonNull(name);
        stop(r, name);
        var args = getAgentArguments(r, name);
        jars.add(args.agentJar);
        start(args, options);
        waitForAgentOnline(r, name, options.loggers);
    }

    public void start(AgentArguments agentArguments, Options options) throws Exception {
        start(agentArguments, options, true);
    }

    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "just for test code")
    public void start(AgentArguments agentArguments, Options options, boolean stop) throws IOException {
        Objects.requireNonNull(options.getName());
        if (stop) {
            stop(Objects.requireNonNull(options.getName()));
        }
        List<String> cmd = new ArrayList<>(List.of(
                JavaEnvUtils.getJreExecutable("java"),
                "-Xmx512m",
                "-XX:+PrintCommandLineFlags",
                "-Djava.awt.headless=true"));
        if (JenkinsRule.SLAVE_DEBUG_PORT > 0) {
            cmd.add("-Xdebug");
            cmd.add("Xrunjdwp:transport=dt_socket,server=y,address="
                    + (JenkinsRule.SLAVE_DEBUG_PORT + agentArguments.numberOfNodes - 1));
        }
        cmd.addAll(options.javaOptions);
        cmd.addAll(List.of("-jar", agentArguments.agentJar.getAbsolutePath()));
        cmd.addAll(List.of("-url", agentArguments.url));
        cmd.addAll(List.of("-name", agentArguments.name));
        cmd.addAll(List.of("-secret", agentArguments.secret));
        if (options.isWebSocket()) {
            cmd.add("-webSocket");
        }
        if (options.getTunnel() != null) {
            cmd.addAll(List.of("-tunnel", options.getTunnel()));
        }

        if (options.noCertificateCheck) {
            cmd.add("-noCertificateCheck");
        } else if (options.cert != null) {
            cmd.addAll(List.of("-cert", options.cert));
        }

        cmd.addAll(agentArguments.commandLineArgs);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.environment().put("INBOUND_AGENT_FIXTURE_ID", id);
        pb.environment().put("INBOUND_AGENT_FIXTURE_NAME", options.getName());
        LOGGER.info(() -> "Running: " + pb.command());
        Process proc = pb.start();
        procs.merge(options.getName(), List.of(proc), (oldValue, newValue) -> {
            // Duplicate agent name, but this can be a valid test case.
            List<Process> result = new ArrayList<>(oldValue);
            result.addAll(newValue);
            return result;
        });
        new StreamCopyThread(
                        "inbound-agent-" + options.getName(),
                        proc.getInputStream(),
                        options.prefixedOutputStreamBuilder.build(System.err))
                .start();
    }

    /**
     * Stop an existing inbound agent and wait for it to go offline.
     */
    public void stop(@NonNull JenkinsRule r, @NonNull String name) throws InterruptedException {
        stop(name);
        waitForAgentOffline(r, name);
    }

    /**
     * Stops an existing inbound agent.
     * You need only call this to simulate an agent crash, followed by {@link #start}.
     */
    public void stop(@NonNull String name) {
        procs.computeIfPresent(name, (k, v) -> {
            stop(name, v);
            return null;
        });
    }

    private static void stop(String name, List<Process> v) {
        for (Process proc : v) {
            LOGGER.info(() -> "Killing " + name + " agent JVM (but not subprocesses)");
            proc.destroyForcibly();
            try {
                proc.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for process to terminate", e);
            }
        }
    }

    /**
     * Checks whether an existing inbound agent process is currently running.
     * (This is distinct from whether Jenkins considers the computer to be connected.)
     */
    public boolean isAlive(String name) {
        return procs.get(name).stream().anyMatch(Process::isAlive);
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "test code")
    public void tearDown() {
        for (var entry : procs.entrySet()) {
            String name = entry.getKey();
            stop(name, entry.getValue());
            try {
                LOGGER.info(() -> "Cleaning up " + name + " agent JVM and/or any subprocesses");
                ProcessTree.get()
                        .killAll(null, Map.of("INBOUND_AGENT_FIXTURE_ID", id, "INBOUND_AGENT_FIXTURE_NAME", name));
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
        procs.clear();
        for (var workDir : workDirs) {
            LOGGER.info(() -> "Deleting " + workDir);
            try {
                FileUtils.deleteDirectory(new File(workDir));
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }
        for (var jar : jars) {
            LOGGER.info(() -> "Deleting " + jar);
            try {
                Files.deleteIfExists(jar.toPath());
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }
    }

    /**
     * Argument for an Agent.
     */
    public static class AgentArguments implements Serializable {

        @NonNull
        private final File agentJar;

        @NonNull
        private final String url;

        @NonNull
        private final String name;

        @NonNull
        private final String secret;

        private final int numberOfNodes;

        @NonNull
        private final List<String> commandLineArgs;

        /**
         * @param agentJar        A reference to the agent jar
         * @param url             the controller root URL
         * @param name            the agent name
         * @param secret          The secret the agent should use to connect.
         * @param numberOfNodes   The number of nodes in the Jenkins instance where the agent is running.
         * @param commandLineArgs Additional command line arguments to pass to the agent.
         */
        public AgentArguments(
                @NonNull File agentJar,
                @NonNull String url,
                @NonNull String name,
                @NonNull String secret,
                int numberOfNodes,
                @NonNull List<String> commandLineArgs) {
            this.agentJar = agentJar;
            this.url = url;
            this.name = name;
            this.secret = secret;
            this.numberOfNodes = numberOfNodes;
            this.commandLineArgs = commandLineArgs;
        }

        @NonNull
        public File agentJar() {
            return agentJar;
        }

        @NonNull
        public String url() {
            return url;
        }

        @NonNull
        public String name() {
            return name;
        }

        @NonNull
        public String secret() {
            return secret;
        }

        public int numberOfNodes() {
            return numberOfNodes;
        }

        @NonNull
        public List<String> commandLineArgs() {
            return commandLineArgs;
        }
    }

    public static AgentArguments getAgentArguments(JenkinsRule r, String name) throws IOException {
        Node node = r.jenkins.getNode(name);
        if (node == null) {
            throw new AssertionError("no such agent: " + name);
        }
        SlaveComputer c = (SlaveComputer) node.toComputer();
        if (c == null) {
            throw new AssertionError("agent " + node + " has no executor");
        }
        JNLPLauncher launcher = (JNLPLauncher) c.getLauncher();
        List<String> commandLineArgs = List.of();
        if (!launcher.getWorkDirSettings().isDisabled()) {
            commandLineArgs = launcher.getWorkDirSettings().toCommandLineArgs(c);
        }
        File agentJar = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "agent", ".jar")
                .toFile();
        FileUtils.copyURLToFile(new Slave.JnlpJar("agent.jar").getURL(), agentJar);
        return new AgentArguments(
                agentJar,
                r.jenkins.getRootUrl(),
                name,
                c.getJnlpMac(),
                r.jenkins.getNodes().size(),
                commandLineArgs);
    }

    public static void waitForAgentOnline(JenkinsRule r, String name, Map<String, Level> loggers) throws Exception {
        Node node = r.jenkins.getNode(name);
        if (node == null) {
            throw new AssertionError("no such agent: " + name);
        }
        if (!(node instanceof Slave)) {
            throw new AssertionError("agent is not a Slave: " + name);
        }
        r.waitOnline((Slave) node);
        if (!loggers.isEmpty()) {
            VirtualChannel channel = node.getChannel();
            assert channel != null;
            channel.call(new JenkinsRule.RemoteLogDumper(null, loggers, false));
        }
    }

    public static void waitForAgentOffline(JenkinsRule r, String name) throws InterruptedException {
        Computer c = r.jenkins.getComputer(name);
        if (c != null) {
            while (c.isOnline()) {
                Thread.sleep(100);
            }
        }
    }

    public static String[] createAgentRJR(JenkinsRule r, Options options) throws Throwable {
        var agent = createAgentJR(r, options);
        return new String[] {options.getName(), agent.getRemoteFS()};
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "just for test code")
    private static Slave createAgentJR(JenkinsRule r, Options options)
            throws Descriptor.FormException, IOException, InterruptedException {
        if (options.getName() == null) {
            options.name = "agent" + r.jenkins.getNodes().size();
        }
        JNLPLauncher launcher = new JNLPLauncher(options.getTunnel());
        DumbSlave s = new DumbSlave(
                Objects.requireNonNull(options.getName()),
                Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), options.getName() + "-work")
                        .toString(),
                launcher);
        s.setLabelString(options.getLabel());
        s.setRetentionStrategy(RetentionStrategy.NOOP);
        r.jenkins.addNode(s);
        // SlaveComputer#_connect runs asynchronously. Wait for it to finish for a more deterministic test.
        Computer computer = s.toComputer();
        while (computer == null || computer.getOfflineCause() == null) {
            Thread.sleep(100);
            computer = s.toComputer();
        }
        return s;
    }
}
