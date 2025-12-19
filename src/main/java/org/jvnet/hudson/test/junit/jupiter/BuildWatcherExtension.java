/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TailLog;
import org.jvnet.hudson.test.fixtures.BuildWatcherFixture;
import org.jvnet.hudson.test.fixtures.JenkinsSessionFixture;

/**
 * Echoes build output to standard error as it arrives.
 * Usage: <pre>{@code
 * @RegisterExtension
 * private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
 * }</pre>
 * Works in combination with {@link JenkinsRule} or {@link JenkinsSessionExtension}.
 * <p>
 * This is the JUnit Jupiter implementation of {@link BuildWatcherFixture}.
 *
 * @see JenkinsRule#waitForCompletion
 * @see JenkinsRule#waitForMessage
 * @see TailLog
 * @see BuildWatcherFixture
 */
public final class BuildWatcherExtension implements BeforeAllCallback, AfterAllCallback {

    private final BuildWatcherFixture fixture =  new BuildWatcherFixture();

    @Override
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    public void beforeAll(@NonNull ExtensionContext extensionContext) {
        fixture.setUp();
    }

    @Override
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    public void afterAll(@NonNull ExtensionContext extensionContext) {
        fixture.tearDown();
    }
}
