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

package org.jvnet.hudson.test.junit.jupiter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Slave;
import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.fixtures.InboundAgentFixture;

/**
 * This is the JUnit Jupiter implementation of {@link InboundAgentFixture}.
 * Usage: <pre>{@code
 * @RegisterExtension
 * private final InboundAgentRule inboundAgent = InboundAgentExtension.newBuilder().build();
 * }</pre>
 *
 * @see InboundAgentFixture
 * @see JenkinsRule#createComputerLauncher
 * @see JenkinsRule#createSlave()
 */
public class InboundAgentExtension implements AfterEachCallback {

    private static final Logger LOGGER = Logger.getLogger(InboundAgentExtension.class.getName());

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
         * <p>Instances of {@link Builder} are created by calling {@link
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
            public Builder name(String name) {
                options.delegate.setName(name);
                return this;
            }

            /**
             * Set a color for agent logs.
             *
             * @param color the color
             * @return this builder
             */
            public Builder color(PrefixedOutputStream.AnsiColor color) {
                options.delegate.getPrefixedOutputStreamBuilder().withColor(color);
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
             *
             * @param websocket use websocket if true, otherwise use inbound TCP
             * @return this builder
             */
            public Builder webSocket(boolean websocket) {
                options.delegate.setWebSocket(websocket);
                return this;
            }

            /**
             * Set a tunnel for the agent
             *
             * @return this builder
             */
            public Builder tunnel(String tunnel) {
                options.delegate.setTunnel(tunnel);
                return this;
            }

            public Builder javaOptions(String... opts) {
                options.delegate.getJavaOptions().addAll(List.of(opts));
                return this;
            }

            /**
             * Provide a custom truststore for the agent JVM. Can be useful when using a setup with a reverse proxy.
             *
             * @param path     the path to the truststore
             * @param password the password for the truststore
             * @return this builder
             */
            public Builder trustStore(String path, String password) {
                options.delegate.setTrustStorePath(path);
                options.delegate.setTrustStorePassword(password);
                return this;
            }

            /**
             * Sets a custom certificate for the agent JVM, passed as the Remoting `-cert` CLI argument.
             * When using {@code RealJenkinsExtension}, use {@link RealJenkinsExtension#getRootCAPem()} to obtain the required value to pass to this method.
             *
             * @param cert the certificate to use
             * @return this builder
             */
            public Builder cert(String cert) {
                options.delegate.setCert(cert);
                return this;
            }

            /**
             * Disables certificate verification for the agent JVM, passed as the Remoting `-noCertificateCheck` CLI argument.
             *
             * @return this builder
             */
            public Builder noCertificateCheck() {
                options.delegate.setNoCertificateCheck(true);
                return this;
            }

            /**
             * Skip starting the agent.
             *
             * @return this builder
             */
            public Builder skipStart() {
                options.delegate.setStart(false);
                return this;
            }

            /**
             * Set a label for the agent.
             *
             * @return this builder.
             */
            public Builder label(String label) {
                options.delegate.setLabel(label);
                return this;
            }

            public Builder withLogger(Class<?> clazz, Level level) {
                return withLogger(clazz.getName(), level);
            }

            public Builder withPackageLogger(Class<?> clazz, Level level) {
                return withLogger(clazz.getPackageName(), level);
            }

            public Builder withLogger(String logger, Level level) {
                options.delegate.getLoggers().put(logger, level);
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
        return fixture.createAgent(r, Options.newBuilder().name(name).build().delegate);
    }

    /**
     * Creates, attaches, and optionally starts a new inbound agent.
     *
     * @param options the options
     */
    public Slave createAgent(@NonNull JenkinsRule r, Options options) throws Exception {
        return fixture.createAgent(r, options.delegate);
    }

    public void createAgent(@NonNull RealJenkinsExtension extension, @CheckForNull String name) throws Throwable {
        createAgent(extension, Options.newBuilder().name(name).build());
    }

    public void createAgent(@NonNull RealJenkinsExtension extension, Options options) throws Throwable {
        var nameAndWorkDir = extension.runRemotely(InboundAgentFixture::createAgentRJR, options.delegate);
        options.delegate.setName(nameAndWorkDir[0]);
        fixture.getWorkDirs().add(nameAndWorkDir[1]);
        if (options.isStart()) {
            start(extension, options);
        }
    }

    /**
     * (Re-)starts an existing inbound agent.
     */
    public void start(@NonNull JenkinsRule r, @NonNull String name) throws Exception {
        fixture.start(r, Options.newBuilder().name(name).build().delegate);
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
    public void start(@NonNull RealJenkinsExtension r, Options options) throws Throwable {
        String name = options.getName();
        Objects.requireNonNull(name);
        stop(r, name);
        startOnly(r, options);
        r.runRemotely(InboundAgentFixture::waitForAgentOnline, name, (LinkedHashMap) options.delegate.getLoggers());
    }

    /**
     * Starts an existing inbound agent without waiting for it to go online.
     */
    public void startOnly(@NonNull RealJenkinsExtension extension, Options options) throws Throwable {
        Objects.requireNonNull(options.getName());
        var args = extension.runRemotely(InboundAgentFixture::getAgentArguments, options.getName());
        fixture.getJars().add(args.agentJar());
        options.delegate.computeJavaOptions(List.of(extension.getTruststoreJavaOptions()));
        fixture.start(args, options.delegate, false);
    }

    public void start(AgentArguments agentArguments, Options options) throws Exception {
        fixture.start(agentArguments.delegate, options.delegate);
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
    public void stop(@NonNull RealJenkinsExtension rjr, @NonNull String name) throws Throwable {
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
    public void afterEach(@NonNull ExtensionContext context) {
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
