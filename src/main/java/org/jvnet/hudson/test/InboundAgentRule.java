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
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.rules.ExternalResource;
import org.jvnet.hudson.test.fixtures.InboundAgentFixture;

/**
 * This is the JUnit 4 implementation of {@link InboundAgentFixture}.
 * Usage: <pre>{@code
 * @Rule
 * public final InboundAgentRule inboundAgent = new InboundAgentRule();
 * }</pre>
 *
 * @see InboundAgentFixture
 * @see JenkinsRule#createComputerLauncher
 * @see JenkinsRule#createSlave()
 */
public final class InboundAgentRule extends ExternalResource {

    private static final Logger LOGGER = Logger.getLogger(InboundAgentRule.class.getName());

    private final InboundAgentFixture fixture = new InboundAgentFixture();

    /**
     * The options used to (re)start an inbound agent.
     */
    public static final class Options extends InboundAgentFixture.Options<Options> {

        private Options(InboundAgentFixture.Options<Options> source) {
            this.name = source.getName();
            this.webSocket = source.isWebSocket();
            this.tunnel = source.getTunnel();
            this.javaOptions = source.getJavaOptions();
            this.start = source.isStart();
            this.loggers = source.getLoggers();
            this.label = source.getLabel();
            this.prefixedOutputStreamBuilder = source.getPrefixedOutputStreamBuilder();
            this.trustStorePath = source.getTrustStorePath();
            this.trustStorePassword = source.getTrustStorePassword();
            this.cert = source.getCert();
            this.noCertificateCheck = source.isNoCertificateCheck();
        }

        /**
         * A builder of {@link Options}.
         *
         * <p>Instances of {@link Options.Builder} are created by calling {@link
         * Options#newBuilder}.
         */
        public static final class Builder extends InboundAgentFixture.Options.Builder<Builder, Options> {

            @Override
            public Options build() {
                return new Options(options);
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
        return fixture.createAgent(r, options);
    }

    public void createAgent(@NonNull RealJenkinsRule rr, @CheckForNull String name) throws Throwable {
        createAgent(rr, Options.newBuilder().name(name).build());
    }

    public void createAgent(@NonNull RealJenkinsRule rr, Options options) throws Throwable {
        var nameAndWorkDir = rr.runRemotely(InboundAgentRule::createAgentRJR, options);
        options.setName(nameAndWorkDir[0]);
        fixture.getWorkDirs().add(nameAndWorkDir[1]);
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
        fixture.start(r, options);
    }

    /**
     * (Re-)starts an existing inbound agent.
     */
    public void start(@NonNull RealJenkinsRule r, Options options) throws Throwable {
        String name = options.getName();
        Objects.requireNonNull(name);
        stop(r, name);
        startOnly(r, options);
        r.runRemotely(InboundAgentRule::waitForAgentOnline, name, (LinkedHashMap) options.getLoggers());
    }

    /**
     * Starts an existing inbound agent without waiting for it to go online.
     */
    public void startOnly(@NonNull RealJenkinsRule r, Options options) throws Throwable {
        Objects.requireNonNull(options.getName());
        var args = r.runRemotely(InboundAgentRule::getAgentArguments, options.getName());
        fixture.getJars().add(args.agentJar());
        options.computeJavaOptions(List.of(r.getTruststoreJavaOptions()));
        fixture.start(args, options, false);
    }

    public void start(AgentArguments agentArguments, Options options) throws Exception {
        fixture.start(agentArguments, options, true);
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
            rjr.runRemotely(InboundAgentRule::waitForAgentOffline, name);
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

    public static class AgentArguments extends InboundAgentFixture.AgentArguments {

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
            super(agentJar, url, name, secret, numberOfNodes, commandLineArgs);
        }

        private AgentArguments(InboundAgentFixture.AgentArguments source) {
            this(
                    source.agentJar(),
                    source.url(),
                    source.name(),
                    source.secret(),
                    source.numberOfNodes(),
                    source.commandLineArgs());
        }
    }

    public static AgentArguments getAgentArguments(JenkinsRule r, String name) throws IOException {
        return new AgentArguments(InboundAgentFixture.getAgentArguments(r, name));
    }

    public static void waitForAgentOnline(JenkinsRule r, String name, Map<String, Level> loggers) throws Exception {
        InboundAgentFixture.waitForAgentOnline(r, name, loggers);
    }

    public static void waitForAgentOffline(JenkinsRule r, String name) throws InterruptedException {
        InboundAgentFixture.waitForAgentOffline(r, name);
    }

    public static String[] createAgentRJR(JenkinsRule r, Options options) throws Throwable {
        return InboundAgentFixture.createAgentRJR(r, options);
    }
}
