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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.runner.Description;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.fixtures.JenkinsSessionFixture;

/**
 * {@link JenkinsRule} derivative which allows Jenkins to be restarted in the middle of a test.
 * It also supports running test code before, between, or after Jenkins sessions,
 * whereas a test method using {@link JenkinsRule} directly will only run after Jenkins has started and must complete before Jenkins terminates.
 */
public class JenkinsSessionExtension implements BeforeEachCallback, AfterEachCallback {

    private final JenkinsSessionFixture fixture = new JenkinsSessionFixture();

    /**
     * Get the Jenkins home directory, which is consistent across restarts.
     */
    public File getHome() {
        return fixture.getHome();
    }

    @Override
    public void beforeEach(@NonNull ExtensionContext context) {
        fixture.setUp(Description.createTestDescription(
                context.getTestClass().map(Class::getName).orElse(null),
                context.getTestMethod().map(Method::getName).orElse(null),
                context.getTestMethod().map(Method::getAnnotations).orElse(null)));
    }

    @Override
    public void afterEach(@NonNull ExtensionContext context) {
        fixture.tearDown();
    }

    /**
     * One step to run, intended to be a SAM for lambdas with {@link #then}.
     */
    @FunctionalInterface
    public interface Step extends JenkinsSessionFixture.Step {}

    /**
     * Run one Jenkins session and shut down.
     */
    public void then(Step s) throws Throwable {
        fixture.then(s);
    }
}
