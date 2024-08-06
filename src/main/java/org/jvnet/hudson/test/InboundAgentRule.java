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

package org.jvnet.hudson.test;

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
import hudson.util.VersionNumber;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.junit.rules.ExternalResource;

/**
 * Manages inbound agents.
 * While these run on the local host, they are launched outside of Jenkins.
 *
 * <p>To avoid flakiness when tearing down the test, ensure that the agent has gone offline with:
 *
 * <pre>
 * Slave agent = inboundAgents.createAgent(r, […]);
 * try {
 *     […]
 * } finally {
 *     inboundAgents.stop(r, agent.getNodeName());
 * }
 * </pre>
 *
 * @see JenkinsRule#createComputerLauncher
 * @see JenkinsRule#createSlave()
 */
public final class InboundAgentRule extends ExternalResource {

    private static final Logger LOGGER = Logger.getLogger(InboundAgentRule.class.getName());

    private final String id = UUID.randomUUID().toString();
    private final Map<String, Process> procs = Collections.synchronizedMap(new HashMap<>());

    /**
     * The options used to (re)start an inbound agent.
     */
    public static final class Options implements Serializable {
        // TODO Java 14+ use records

        @CheckForNull private String name;

        /**
         * @deprecated secret is used by default when using newer versions of Remoting
         */
        @Deprecated
        private boolean secret;
        private boolean webSocket;
        @CheckForNull private String tunnel;
        private boolean start = true;
        private final LinkedHashMap<String, Level> loggers = new LinkedHashMap<>();
        private String label;
        private final PrefixedOutputStream.Builder prefixedOutputStreamBuilder = PrefixedOutputStream.builder();

        public String getName() {
            return name;
        }

        /**
         * @deprecated secret is used by default when using newer versions of Remoting
         */
        @Deprecated
        public boolean isSecret() {
            return secret;
        }

        public boolean isWebSocket() {
            return webSocket;
        }

        public String getTunnel() {
            return tunnel;
        }

        public boolean isStart() {
            return start;
        }

        public String getLabel() {
            return label;
        }

        /**
         * A builder of {@link Options}.
         *
         * <p>Instances of {@link Builder} are created by calling {@link
         * InboundAgentRule.Options#newBuilder}.
         */
        public static final class Builder {

            private final Options options = new Options();

            private Builder() {}

            /**
             * Set the name of the agent.
             *
             * @param name the name
             * @return this builder
             */
            public Builder name(String name) {
                options.name = name;
                return this;
            }

            /**
             * Set a color for agent logs.
             *
             * @param color the color
             * @return this builder
             */
            public Builder color(PrefixedOutputStream.AnsiColor color) {
                options.prefixedOutputStreamBuilder.withColor(color);
                return this;
            }

            /**
             * Use secret when connecting.
             *
             * @return this builder
             * @deprecated secret is used by default when using newer versions of Remoting
             */
            @Deprecated
            public Builder secret() {
                options.secret = true;
                return this;
            }

            /**
             * Use WebSocket when connecting.
             *
             * @return this builder
             */
            public Builder webSocket() {
                return webSocket(true);
            }

            /**
             * Configure usage of WebSocket when connecting.
             * @param websocket use websocket if true, otherwise use inbound TCP
             *
             * @return this builder
             */
            public Builder webSocket(boolean websocket) {
                options.webSocket = websocket;
                return this;
            }

            /**
             * Set a tunnel for the agent
             *
             * @return this builder
             */
            public Builder tunnel(String tunnel) {
                options.tunnel = tunnel;
                return this;
            }

            /**
             * Skip starting the agent.
             *
             * @return this builder
             */
            public Builder skipStart() {
                options.start = false;
                return this;
            }

            /**
             * Set a label for the agent.
             *
             * @return this builder.
             */
            public Builder label(String label) {
                options.label = label;
                return this;
            }

            public Builder withLogger(Class<?> clazz, Level level) {
                return withLogger(clazz.getName(), level);
            }

            public Builder withPackageLogger(Class<?> clazz, Level level) {
                return withLogger(clazz.getPackageName(), level);
            }

            public Builder withLogger(String logger, Level level) {
                options.loggers.put(logger, level);
                return this;
            }


            /**
             * Build and return an {@link Options}.
             *
             * @return a new {@link Options}
             */
            public Options build() {
                return options;
            }
        }

        public static Builder newBuilder() {
            return new Builder();
        }
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
        if (options.isStart()) {
            start(r, options);
        }
        return s;
    }

    public void createAgent(@NonNull RealJenkinsRule rr, @CheckForNull String name) throws Throwable {
        createAgent(rr, Options.newBuilder().name(name).build());
    }

    public void createAgent(@NonNull RealJenkinsRule rr, Options options) throws Throwable {
        String name = rr.runRemotely(InboundAgentRule::createAgentRJR, options);
        options.name = name;
        if (options.isStart()) {
            start(rr, options);
        }
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
        start(getAgentArguments(r, name), options);
        waitForAgentOnline(r, name, options.loggers);
    }

    /**
     * (Re-)starts an existing inbound agent.
     */
    public void start(@NonNull RealJenkinsRule r, Options options) throws Throwable {
        String name = options.getName();
        Objects.requireNonNull(name);
        stop(r, name);
        start(r.runRemotely(InboundAgentRule::getAgentArguments, name), options);
        r.runRemotely(InboundAgentRule::waitForAgentOnline, name, options.loggers);
    }

    @SuppressFBWarnings(value = {"COMMAND_INJECTION", "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"}, justification = "just for test code")
    public void start(AgentArguments agentArguments, Options options) throws Exception {
        Objects.requireNonNull(options.getName());
        stop(options.getName());
        List<String> cmd = new ArrayList<>(List.of(JavaEnvUtils.getJreExecutable("java"),
            "-Xmx512m",
            "-XX:+PrintCommandLineFlags",
            "-Djava.awt.headless=true"));
        if (JenkinsRule.SLAVE_DEBUG_PORT > 0) {
            cmd.add("-Xdebug");
            cmd.add("Xrunjdwp:transport=dt_socket,server=y,address=" + (JenkinsRule.SLAVE_DEBUG_PORT + agentArguments.numberOfNodes - 1));
        }
        cmd.addAll(List.of("-jar", agentArguments.agentJar.getAbsolutePath()));
        if (agentArguments.agentJnlpUrl.endsWith("computer/" + options.getName() + "/slave-agent.jnlp") && remotingVersion(agentArguments.agentJar).isNewerThanOrEqualTo(new VersionNumber("3186.vc3b_7249b_87eb_"))) {
            cmd.addAll(List.of("-url", agentArguments.agentJnlpUrl.replaceAll("computer/" + options.getName() + "/slave-agent.jnlp$", "")));
            cmd.addAll(List.of("-secret", agentArguments.secret));
            cmd.addAll(List.of("-name", options.getName()));
            if (options.isWebSocket()) {
                cmd.add("-webSocket");
            }
            if (options.getTunnel() != null) {
                cmd.addAll(List.of("-tunnel", options.getTunnel()));
            }
        } else {
            cmd.addAll(List.of("-jnlpUrl", agentArguments.agentJnlpUrl));
            if (options.isSecret()) {
                // To watch it fail: secret = secret.replace('1', '2');
                cmd.addAll(List.of("-secret", agentArguments.secret));
            }
        }
        cmd.addAll(agentArguments.commandLineArgs);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.environment().put("INBOUND_AGENT_RULE_ID", id);
        pb.environment().put("INBOUND_AGENT_RULE_NAME", options.getName());
        LOGGER.info(() -> "Running: " + pb.command());
        Process proc = pb.start();
        procs.put(options.getName(), proc);
        new StreamCopyThread("inbound-agent-" + options.getName(), proc.getInputStream(), options.prefixedOutputStreamBuilder.build(System.err)).start();
    }

    private static VersionNumber remotingVersion(File agentJar) throws IOException {
        try (JarFile j = new JarFile(agentJar)) {
            String v = j.getManifest().getMainAttributes().getValue("Version");
            if (v == null) {
                throw new IOException("no Version in " + agentJar);
            }
            return new VersionNumber(v);
        }
    }

    /**
     * Stop an existing inbound agent and wait for it to go offline.
     */
    public void stop(@NonNull JenkinsRule r, @NonNull String name) throws InterruptedException {
        stop(name);
        waitForAgentOffline(r, name);
    }

    /**
     * Stop an existing inbound agent and wait for it to go offline.
     */
    public void stop(@NonNull RealJenkinsRule rjr, @NonNull String name) throws Throwable {
        stop(name);
        if (rjr.isAlive()) {
            rjr.runRemotely(InboundAgentRule::waitForAgentOffline, name);
        } else {
            LOGGER.warning(() -> "Controller seems to have already shut down; not waiting for " + name + " to go offline");
        }
    }

    /**
     * Stops an existing inbound agent.
     * You need only call this to simulate an agent crash, followed by {@link #start}.
     */
    public void stop(@NonNull String name) throws InterruptedException {
        Process proc = procs.put(name, null);
        if (proc != null) {
            LOGGER.info(() -> "Killing " + name + " agent JVM (but not subprocesses)");
            proc.destroyForcibly();
            proc.waitFor();
        } else {
            // (normal when called from #start)
            LOGGER.fine(() -> "No " + name + " agent JVM found to kill");
        }
    }

    /**
     * Checks whether an existing inbound agent process is currently running.
     * (This is distinct from whether Jenkins considers the computer to be connected.)
     */
    public boolean isAlive(String name) {
        Process proc = procs.get(name);
        return proc != null && proc.isAlive();
    }

    @Override protected void after() {
        for (var entry : procs.entrySet()) {
            String name = entry.getKey();
            Process proc = entry.getValue();
            try {
                LOGGER.info(() -> "Cleaning up " + name + " agent JVM and/or any subprocesses");
                ProcessTree.get().killAll(proc, Map.of("INBOUND_AGENT_RULE_ID", id, "INBOUND_AGENT_RULE_NAME", name));
                if (proc != null) {
                    proc.destroyForcibly();
                    proc.waitFor();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
        procs.clear();
    }

    public static class AgentArguments implements Serializable {
        /**
         * URL to the agent JNLP file.
         */
        @NonNull
        private final String agentJnlpUrl;
        /**
         * A reference to the agent jar
         */
        @NonNull
        private final File agentJar;
        /**
         * The secret the agent should use to connect.
         */
        @NonNull
        private final String secret;
        /**
         * The number of nodes in the Jenkins instance where the agent is running.
         */
        private final int numberOfNodes;
        /**
         * Additional command line arguments to pass to the agent.
         */
        @NonNull
        private final List<String> commandLineArgs;

        public AgentArguments(@NonNull String agentJnlpUrl, @NonNull File agentJar, @NonNull String secret, int numberOfNodes, @NonNull List<String> commandLineArgs) {
            this.agentJnlpUrl = agentJnlpUrl;
            this.agentJar = agentJar;
            this.secret = secret;
            this.numberOfNodes = numberOfNodes;
            this.commandLineArgs = commandLineArgs;
        }
    }

    private static AgentArguments getAgentArguments(JenkinsRule r, String name) throws IOException {
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
        File agentJar = new File(r.jenkins.getRootDir(), "agent.jar");
        if (!agentJar.isFile()) {
            FileUtils.copyURLToFile(new Slave.JnlpJar("agent.jar").getURL(), agentJar);
        }
        return new AgentArguments(r.jenkins.getRootUrl() + "computer/" + name + "/slave-agent.jnlp", agentJar, c.getJnlpMac(), r.jenkins.getNodes().size(), commandLineArgs);
    }

    private static void waitForAgentOnline(JenkinsRule r, String name, Map<String, Level> loggers) throws Exception {
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

    private static void waitForAgentOffline(JenkinsRule r, String name) throws InterruptedException {
        Computer c = r.jenkins.getComputer(name);
        if (c != null) {
            while (c.isOnline()) {
                Thread.sleep(100);
            }
        }
    }

    private static String createAgentRJR(JenkinsRule r, Options options) throws Throwable {
        createAgentJR(r, options);
        return options.getName();
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "just for test code")
    private static Slave createAgentJR(JenkinsRule r, Options options) throws Descriptor.FormException, IOException, InterruptedException {
        if (options.getName() == null) {
            options.name = "agent" + r.jenkins.getNodes().size();
        }
        JNLPLauncher launcher = new JNLPLauncher(options.getTunnel());
        launcher.setWebSocket(options.isWebSocket());
        DumbSlave s = new DumbSlave(options.getName(), new File(r.jenkins.getRootDir(), "agent-work-dirs/" + options.getName()).getAbsolutePath(), launcher);
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
