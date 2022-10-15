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
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.util.StreamCopyThread;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
 * r.waitOnline(agent);
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

    private final ConcurrentMap<String, Process> procs = new ConcurrentHashMap<>();

    /**
     * The options used to (re)start an inbound agent.
     */
    public static class Options {
        // TODO Java 14+ use records

        @CheckForNull private String name;
        private boolean secret;
        private boolean webSocket;
        private boolean start = true;

        public String getName() {
            return name;
        }

        public void setName(@CheckForNull String name) {
            this.name = name;
        }

        public boolean isSecret() {
            return secret;
        }

        public void setSecret(boolean secret) {
            this.secret = secret;
        }

        public boolean isWebSocket() {
            return webSocket;
        }

        public void setWebSocket(boolean webSocket) {
            this.webSocket = webSocket;
        }

        public boolean isStart() {
            return start;
        }

        public void setStart(boolean start) {
            this.start = start;
        }

        /**
         * A builder of {@link Options}.
         *
         * <p>Instances of {@link Builder} are created by calling {@link
         * InboundAgentRule.Options#newBuilder}.
         */
        public interface Builder {

            /**
             * Set the name of the agent.
             *
             * @param name the name
             * @return this builder
             */
            Builder name(String name);

            /**
             * Use secret when connecting.
             *
             * @return this builder
             */
            Builder secret();

            /**
             * Use WebSocket when connecting.
             *
             * @return this builder
             */
            Builder webSocket();

            /**
             * Skip starting the agent.
             *
             * @return this builder
             */
            Builder skipStart();

            /**
             * Build and return an {@link Options}.
             *
             * @return a new {@link Options}
             */
            Options build();
        }

        private static class BuilderImpl implements Builder {

            private final Options options = new Options();

            @Override
            public Builder name(String name) {
                options.setName(name);
                return this;
            }

            @Override
            public Builder secret() {
                options.setSecret(true);
                return this;
            }

            @Override
            public Builder webSocket() {
                options.setWebSocket(true);
                return this;
            }

            @Override
            public Builder skipStart() {
                options.setStart(false);
                return this;
            }

            @Override
            public Options build() {
                return options;
            }
        }

        public static Builder newBuilder() {
            return new BuilderImpl();
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
    @SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN", "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"}, justification = "just for test code")
    public Slave createAgent(@NonNull JenkinsRule r, Options options) throws Exception {
        if (options.getName() == null) {
            options.setName("agent" + r.jenkins.getNodes().size());
        }
        JNLPLauncher launcher = new JNLPLauncher(true);
        launcher.setWebSocket(options.isWebSocket());
        DumbSlave s = new DumbSlave(options.getName(), new File(r.jenkins.getRootDir(), "agent-work-dirs/" + options.getName()).getAbsolutePath(), launcher);
        s.setRetentionStrategy(RetentionStrategy.NOOP);
        r.jenkins.addNode(s);
        // SlaveComputer#_connect runs asynchronously. Wait for it to finish for a more deterministic test.
        while (s.toComputer().getOfflineCause() == null) {
            Thread.sleep(100);
        }
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
    @SuppressFBWarnings(value = {"COMMAND_INJECTION", "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"}, justification = "just for test code")
    public void start(@NonNull JenkinsRule r, Options options) throws Exception {
        Objects.requireNonNull(options.getName());
        stop(options.getName());
        List<String> cmd = new ArrayList<>(Arrays.asList(JavaEnvUtils.getJreExecutable("java"),
            "-Xmx512m",
            "-XX:+PrintCommandLineFlags",
            "-Djava.awt.headless=true"));
        if (JenkinsRule.SLAVE_DEBUG_PORT > 0) {
            cmd.add("-Xdebug");
            cmd.add("Xrunjdwp:transport=dt_socket,server=y,address=" + (JenkinsRule.SLAVE_DEBUG_PORT + r.jenkins.getNodes().size() - 1));
        }
        File agentJar = new File(r.jenkins.getRootDir(), "agent.jar");
        if (!agentJar.isFile()) {
            FileUtils.copyURLToFile(new Slave.JnlpJar("agent.jar").getURL(), agentJar);
        }
        cmd.addAll(Arrays.asList(
            "-jar", agentJar.getAbsolutePath(),
            "-jnlpUrl", r.getURL() + "computer/" + options.getName() + "/slave-agent.jnlp"));
        SlaveComputer c = (SlaveComputer) r.jenkins.getNode(options.getName()).toComputer();
        if (options.isSecret()) {
            String secret = c.getJnlpMac();
            // To watch it fail: secret = secret.replace('1', '2');
            cmd.addAll(Arrays.asList("-secret", secret));
        }
        JNLPLauncher launcher = (JNLPLauncher) c.getLauncher();
        if (!launcher.getWorkDirSettings().isDisabled()) {
            cmd.addAll(launcher.getWorkDirSettings().toCommandLineArgs(c));
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        System.err.println("Running: " + pb.command());
        Process proc = pb.start();
        procs.put(options.getName(), proc);
        new StreamCopyThread("jnlp", proc.getInputStream(), System.err).start();
    }

    /**
     * Stop an existing inbound agent and wait for it to go offline.
     */
    public void stop(@NonNull JenkinsRule r, @NonNull String name) throws InterruptedException {
        stop(name);
        Computer c = r.jenkins.getComputer(name);
        if (c != null) {
            while (c.isOnline()) {
                Thread.sleep(100);
            }
        }
    }

    /**
     * Stops an existing inbound agent.
     * You need only call this to simulate an agent crash, followed by {@link #start}.
     */
    public void stop(@NonNull String name) throws InterruptedException {
        Process proc = procs.remove(name);
        if (proc != null) {
            proc.destroyForcibly();
            proc.waitFor();
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
        for (String name : procs.keySet()) {
            try {
                stop(name);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

}
