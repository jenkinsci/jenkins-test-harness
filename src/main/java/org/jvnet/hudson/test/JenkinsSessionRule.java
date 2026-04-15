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
import java.lang.annotation.Annotation;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.fixtures.JenkinsSessionFixture;

/**
 * This is the JUnit 4 implementation of {@link JenkinsSessionFixture}.
 * Usage: <pre>{@code
 * @Rule
 * public final JenkinsSessionRule jenkinsSession = new JenkinsSessionRule();
 * }</pre>
 *
 * @see JenkinsSessionFixture
 * @see JenkinsRule#createComputerLauncher
 * @see JenkinsRule#createSlave()
 */
public class JenkinsSessionRule implements TestRule {

    private final JenkinsSessionFixture fixture = new JenkinsSessionFixture();
    private Description description;

    /**
     * Get the Jenkins home directory, which is consistent across restarts.
     */
    public File getHome() {
        return fixture.getHome();
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        this.description = description;
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    fixture.setUp(
                            description.getClassName(),
                            description.getMethodName(),
                            description.getAnnotations().toArray(new Annotation[0]));
                    base.evaluate();
                } finally {
                    fixture.tearDown();
                }
            }
        };
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
        if (description == null) {
            throw new IllegalStateException("JenkinsSessionRule must be registered via @Rule");
        }
        fixture.then(s);
    }
}
