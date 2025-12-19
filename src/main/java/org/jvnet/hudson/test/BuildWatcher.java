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

package org.jvnet.hudson.test;

import org.junit.rules.ExternalResource;
import org.jvnet.hudson.test.fixtures.BuildWatcherFixture;

/**
 * This is the JUnit 4 implementation of {@link BuildWatcherFixture}.
 * Usage: <pre>{@code
 * @ClassRule
 * public static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcher();
 * }</pre>
 * Works in combination with {@link JenkinsRule} or {@link JenkinsSessionRule}.
 *
 * @see BuildWatcherFixture
 * @see JenkinsRule
 * @see JenkinsSessionRule
 * @since 1.607
 */
public final class BuildWatcher extends ExternalResource {

    private static final BuildWatcherFixture FIXTURE = new BuildWatcherFixture();

    @Override
    protected void before() throws Throwable {
        FIXTURE.setUp();
    }

    @Override
    protected void after() {
        FIXTURE.tearDown();
    }
}
