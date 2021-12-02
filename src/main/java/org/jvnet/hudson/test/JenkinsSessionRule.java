/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Simpler alternative to {@link RestartableJenkinsRule}.
 * Most critically, {@link #then} runs immediately, so this rule plays nicely with things like {@link After}.
 */
public class JenkinsSessionRule implements TestRule {

    private static final Logger LOGGER = Logger.getLogger(JenkinsSessionRule.class.getName());

    private Description description;

    private final TemporaryDirectoryAllocator tmp = new TemporaryDirectoryAllocator();

    /**
     * JENKINS_HOME needs to survive restarts, so we allocate our own.
     */
    private File home;

    /**
     * TCP/IP port that the server is listening on.
     * Like the home directory, this will be consistent across restarts.
     */
    private int port;

    /**
     * Get the Jenkins home directory, which is consistent across restarts.
     */
    public File getHome() {
        if (home == null) {
            throw new IllegalStateException("JENKINS_HOME has not been allocated yet");
        }
        return home;
    }

    @Override public Statement apply(final Statement base, Description description) {
        this.description = description;
        return new Statement() {
            @Override public void evaluate() throws Throwable {
                try {
                    home = tmp.allocate();
                    base.evaluate();
                } finally {
                    try {
                        tmp.dispose();
                    } catch (Exception x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
            }
        };
    }

    /**
     * One step to run, intended to be a SAM for lambdas with {@link #then}.
     */
    @FunctionalInterface
    public interface Step {
        void run(JenkinsRule r) throws Throwable;
    }

    /**
     * Run one Jenkins session and shut down.
     */
    public void then(Step s) throws Throwable {
        CustomJenkinsRule r = new CustomJenkinsRule(home, port);
        r.apply(new Statement() {
            @Override public void evaluate() throws Throwable {
                port = r.getPort();
                s.run(r);
            }
        }, description).evaluate();
    }

    private static final class CustomJenkinsRule extends JenkinsRule {
        CustomJenkinsRule(File home, int port) {
            with(() -> home);
            localPort = port;
        }
        int getPort() {
            return localPort;
        }
    }

}
