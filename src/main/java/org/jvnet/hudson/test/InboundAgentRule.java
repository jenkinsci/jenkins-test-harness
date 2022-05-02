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
import hudson.model.Slave;
import hudson.remoting.Launcher;
import hudson.remoting.Which;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.util.StreamCopyThread;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.junit.rules.ExternalResource;

/**
 * Manages inbound agents.
 * While these run on the local host, they are launched outside of Jenkins.
 * @see JenkinsRule#createComputerLauncher
 * @see JenkinsRule#createSlave()
 */
public final class InboundAgentRule extends ExternalResource {

    private final Map<String, Process> procs = new HashMap<>();

    /**
     * Creates, attaches, and starts a new inbound agent.
     * @param name an optional {@link Slave#getNodeName}
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "just for test code")
    public Slave createAgent(@NonNull JenkinsRule r, @CheckForNull String name) throws Exception {
        if (name == null) {
            name = "agent" + r.jenkins.getNodes().size();
        }
        // TODO 2.216+ support WebSocket option
        DumbSlave s = new DumbSlave(name, new File(r.jenkins.getRootDir(), "agent-work-dirs/" + name).getAbsolutePath(), new JNLPLauncher(true));
        s.setNumExecutors(1); // TODO pending 2.234+
        s.setRetentionStrategy(RetentionStrategy.NOOP);
        r.jenkins.addNode(s);
        start(r, name);
        return s;
    }

    /**
     * (Re-)starts an existing inbound agent.
     */
    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "just for test code")
    public void start(@NonNull JenkinsRule r, String name) throws Exception {
        stop(name);
        ProcessBuilder pb = new ProcessBuilder(JavaEnvUtils.getJreExecutable("java"), "-Djava.awt.headless=true", "-jar", Which.jarFile(Launcher.class).getAbsolutePath(), "-jnlpUrl", r.getURL() + "computer/" + name + "/slave-agent.jnlp");
        pb.redirectErrorStream(true);
        System.err.println("Running: " + pb.command());
        Process proc = pb.start();
        procs.put(name, proc);
        new StreamCopyThread("jnlp", proc.getInputStream(), System.err).start();
    }

    /**
     * Stops an existing inbound agent.
     * You need only call this to simulate an agent crash, followed by {@link #start}.
     */
    public void stop(String name) {
        Process proc = procs.remove(name);
        if (proc != null) {
            proc.destroyForcibly();
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
            stop(name);
        }
    }

}
