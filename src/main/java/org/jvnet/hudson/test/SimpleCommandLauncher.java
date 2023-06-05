/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.ProcessTree;
import hudson.util.StreamCopyThread;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Stripped-down clone of {@code CommandLauncher}.
 */
public class SimpleCommandLauncher extends ComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(SimpleCommandLauncher.class.getName());

    public final String cmd;
    private final Map<String, String> env;

    @DataBoundConstructor // in case anyone needs to configRoundtrip such a node
    public SimpleCommandLauncher(String cmd) {
        this(cmd, null);
    }

    SimpleCommandLauncher(String cmd, EnvVars env) {
        this.cmd = cmd;
        this.env = env != null ? new HashMap<>(env) : null;
    }

    @Override
    public void launch(SlaveComputer computer, final TaskListener listener) {
        try {
            Slave node = computer.getNode();
            if (node == null) {
                throw new AbortException("Cannot launch commands on deleted nodes");
            }
            listener.getLogger().println("$ " + cmd);
            ProcessBuilder pb = new ProcessBuilder(Util.tokenize(cmd));
            final EnvVars cookie = EnvVars.createCookie();
            pb.environment().putAll(cookie);
            if (env != null) {
            	pb.environment().putAll(env);
            }
            final Process proc = pb.start();
            new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(), proc.getErrorStream(), listener.getLogger()).start();
            computer.setChannel(proc.getInputStream(), proc.getOutputStream(), listener.getLogger(), new Channel.Listener() {
                @Override
                public void onClosed(Channel channel, IOException cause) {
                    try {
                        ProcessTree.get().killAll(proc, cookie);
                    } catch (Exception x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
            });
            LOGGER.log(Level.INFO, "agent launched for {0}", computer.getName());
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {}
}
