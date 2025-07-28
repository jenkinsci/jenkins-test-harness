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

package org.jvnet.hudson.test.junit.jupiter;

import java.io.File;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;

/**
 * {@link JenkinsRule} derivative which allows Jenkins to be restarted in the middle of a test.
 * It also supports running test code before, between, or after Jenkins sessions,
 * whereas a test method using {@link JenkinsRule} directly will only run after Jenkins has started and must complete before Jenkins terminates.
 */
public class JenkinsSessionExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger LOGGER = Logger.getLogger(JenkinsSessionExtension.class.getName());

    private final TemporaryDirectoryAllocator tmp = new TemporaryDirectoryAllocator();

    private ExtensionContext extensionContext;

    private Description description;

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

    @Override
    public void beforeEach(ExtensionContext context) {
        extensionContext = context;

        description = Description.createTestDescription(
                extensionContext.getTestClass().map(Class::getName).orElse(null),
                extensionContext.getTestMethod().map(Method::getName).orElse(null),
                extensionContext.getTestMethod().map(Method::getAnnotations).orElse(null));

        try {
            home = tmp.allocate();
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        try {
            tmp.dispose();
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
        }
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
        r.apply(
                        new Statement() {
                            @Override
                            public void evaluate() throws Throwable {
                                port = r.getPort();
                                s.run(r);
                            }
                        },
                        description)
                .evaluate();
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
