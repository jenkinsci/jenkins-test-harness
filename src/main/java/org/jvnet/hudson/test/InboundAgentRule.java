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
import hudson.model.Slave;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.rules.ExternalResource;
import org.jvnet.hudson.test.fixtures.InboundAgentFixture;

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

    private final InboundAgentFixture fixture = new InboundAgentFixture();

    /**
     * The options used to (re)start an inbound agent.
     */
    public static final class Options implements Serializable {

        private final InboundAgentFixture.Options delegate = new InboundAgentFixture.Options();

        public String getName() {
            return delegate.getName();
        }

        public boolean isWebSocket() {
            return delegate.isWebSocket();
        }

        public String getTunnel() {
            return delegate.getTunnel();
        }

        public boolean isStart() {
            return delegate.isStart();
        }

        public String getLabel() {
            return delegate.getLabel();
        }

        /**
         * A builder of {@link Options}.
         *
         * <p>Instances of {@link Options.Builder} are created by calling {@link
         * Options#newBuilder}.
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
            public Options.Builder name(String name) {
                options.delegate.name = name;
                return this;
            }

            /**
             * Set a color for agent logs.
             *
             * @param color the color
             * @return this builder
             */
            public Options.Builder color(PrefixedOutputStream.AnsiColor color) {
                options.delegate.prefixedOutputStreamBuilder.withColor(color);
                return this;
            }

            /**
             * Use WebSocket when connecting.
             *
             * @return this builder
             */
            public Options.Builder webSocket() {
                return webSocket(true);
            }

            /**
             * Configure usage of WebSocket when connecting.
             *
             * @param websocket use websocket if true, otherwise use inbound TCP
             * @return this builder
             */
            public Options.Builder webSocket(boolean websocket) {
                options.delegate.webSocket = websocket;
                return this;
            }

            /**
             * Set a tunnel for the agent
             *
             * @return this builder
             */
            public Options.Builder tunnel(String tunnel) {
                options.delegate.tunnel = tunnel;
                return this;
            }

            public Options.Builder javaOptions(String... opts) {
                options.delegate.javaOptions.addAll(List.of(opts));
                return this;
            }

            /**
             * Provide a custom truststore for the agent JVM. Can be useful when using a setup with a reverse proxy.
             *
             * @param path     the path to the truststore
             * @param password the password for the truststore
             * @return this builder
             */
            public Options.Builder trustStore(String path, String password) {
                options.delegate.trustStorePath = path;
                options.delegate.trustStorePassword = password;
                return this;
            }

            /**
             * Sets a custom certificate for the agent JVM, passed as the Remoting `-cert` CLI argument.
             * When using {@code RealJenkinsRule}, use {@link RealJenkinsRule#getRootCAPem()} to obtain the required value to pass to this method.
             *
             * @param cert the certificate to use
             * @return this builder
             */
            public Options.Builder cert(String cert) {
                options.delegate.cert = cert;
                return this;
            }

            /**
             * Disables certificate verification for the agent JVM, passed as the Remoting `-noCertificateCheck` CLI argument.
             *
             * @return this builder
             */
            public Options.Builder noCertificateCheck() {
                options.delegate.noCertificateCheck = true;
                return this;
            }

            /**
             * Skip starting the agent.
             *
             * @return this builder
             */
            public Options.Builder skipStart() {
                options.delegate.start = false;
                return this;
            }

            /**
             * Set a label for the agent.
             *
             * @return this builder.
             */
            public Options.Builder label(String label) {
                options.delegate.label = label;
                return this;
            }

            public Options.Builder withLogger(Class<?> clazz, Level level) {
                return withLogger(clazz.getName(), level);
            }

            public Options.Builder withPackageLogger(Class<?> clazz, Level level) {
                return withLogger(clazz.getPackageName(), level);
            }

            public Options.Builder withLogger(String logger, Level level) {
                options.delegate.loggers.put(logger, level);
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

        public static Options.Builder newBuilder() {
            return new Options.Builder();
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
        return fixture.createAgent(r, options.delegate);
    }

    public void createAgent(@NonNull RealJenkinsRule rr, @CheckForNull String name) throws Throwable {
        createAgent(rr, Options.newBuilder().name(name).build());
    }

    public void createAgent(@NonNull RealJenkinsRule rr, Options options) throws Throwable {
        var nameAndWorkDir = rr.runRemotely(InboundAgentFixture::createAgentRJR, options.delegate);
        options.delegate.name = nameAndWorkDir[0];
        fixture.workDirs.add(nameAndWorkDir[1]);
        if (options.isStart()) {
            start(rr, options);
        }
    }

    /**
     * (Re-)starts an existing inbound agent.
     */
    public void start(@NonNull JenkinsRule r, @NonNull String name) throws Exception {
        fixture.start(r, name);
    }

    /**
     * (Re-)starts an existing inbound agent.
     */
    public void start(@NonNull JenkinsRule r, Options options) throws Exception {
        fixture.start(r, options.delegate);
    }

    /**
     * (Re-)starts an existing inbound agent.
     */
    public void start(@NonNull RealJenkinsRule r, Options options) throws Throwable {
        String name = options.getName();
        Objects.requireNonNull(name);
        stop(r, name);
        startOnly(r, options);
        r.runRemotely(InboundAgentFixture::waitForAgentOnline, name, options.delegate.loggers);
    }

    /**
     * Starts an existing inbound agent without waiting for it to go online.
     */
    public void startOnly(@NonNull RealJenkinsRule r, Options options) throws Throwable {
        Objects.requireNonNull(options.getName());
        var args = r.runRemotely(InboundAgentFixture::getAgentArguments, options.getName());
        fixture.jars.add(args.agentJar());
        options.delegate.computeJavaOptions(List.of(r.getTruststoreJavaOptions()));
        fixture.start(args, options.delegate, false);
    }

    public void start(AgentArguments agentArguments, Options options) throws Exception {
        fixture.start(agentArguments.delegate, options.delegate, true);
    }

    /**
     * Stop an existing inbound agent and wait for it to go offline.
     */
    public void stop(@NonNull JenkinsRule r, @NonNull String name) throws InterruptedException {
        fixture.stop(r, name);
    }

    /**
     * Stop an existing inbound agent and wait for it to go offline.
     */
    public void stop(@NonNull RealJenkinsRule rjr, @NonNull String name) throws Throwable {
        stop(name);
        if (rjr.isAlive()) {
            rjr.runRemotely(InboundAgentFixture::waitForAgentOffline, name);
        } else {
            LOGGER.warning(
                    () -> "Controller seems to have already shut down; not waiting for " + name + " to go offline");
        }
    }

    /**
     * Stops an existing inbound agent.
     * You need only call this to simulate an agent crash, followed by {@link #start}.
     */
    public void stop(@NonNull String name) {
        fixture.stop(name);
    }

    /**
     * Checks whether an existing inbound agent process is currently running.
     * (This is distinct from whether Jenkins considers the computer to be connected.)
     */
    public boolean isAlive(String name) {
        return fixture.isAlive(name);
    }

    @Override
    protected void after() {
        fixture.tearDown();
    }

    public static class AgentArguments implements Serializable {

        private final InboundAgentFixture.AgentArguments delegate;

        public AgentArguments(
                @NonNull File agentJar,
                @NonNull String url,
                @NonNull String name,
                @NonNull String secret,
                int numberOfNodes,
                @NonNull List<String> commandLineArgs) {
            delegate =
                    new InboundAgentFixture.AgentArguments(agentJar, url, name, secret, numberOfNodes, commandLineArgs);
        }
    }
}
