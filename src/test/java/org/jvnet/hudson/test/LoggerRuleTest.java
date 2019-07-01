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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;
import org.junit.Rule;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.jvnet.hudson.test.LoggerRule.recorded;

public class LoggerRuleTest {

    @Rule
    public LoggerRule logRule = new LoggerRule();

    private static final Logger FOO_LOGGER = Logger.getLogger("Foo");
    private static final Logger BAR_LOGGER = Logger.getLogger("Bar");

    @Test
    public void testRecordedSingleLogger() {
        logRule.record("Foo", Level.INFO).capture(1);
        FOO_LOGGER.log(Level.INFO, "Entry 1");
        assertThat(logRule, recorded(equalTo("Entry 1")));
        assertThat(logRule, recorded(Level.INFO, equalTo("Entry 1")));
        assertThat(logRule, not(recorded(Level.WARNING, equalTo("Entry 1"))));
        FOO_LOGGER.log(Level.INFO, "Entry 2");
        assertThat(logRule, not(recorded(equalTo("Entry 1"))));
        assertThat(logRule, recorded(equalTo("Entry 2")));
    }

    @Test
    public void testRecordedMultipleLoggers() {
        logRule.record("Foo", Level.INFO).record("Bar", Level.SEVERE).capture(2);
        FOO_LOGGER.log(Level.INFO, "Foo Entry 1");
        BAR_LOGGER.log(Level.SEVERE, "Bar Entry 1");
        assertThat(logRule, recorded(equalTo("Foo Entry 1")));
        assertThat(logRule, recorded(equalTo("Bar Entry 1")));
        // All criteria must match a single LogRecord.
        assertThat(logRule, not(recorded(Level.INFO, equalTo("Bar Entry 1"))));
    }

    @Test
    public void testRecordedThrowable() {
        logRule.record("Foo", Level.INFO).capture(1);
        FOO_LOGGER.log(Level.INFO, "Foo Entry 1", new IllegalStateException());
        assertThat(logRule, recorded(equalTo("Foo Entry 1"), instanceOf(IllegalStateException.class)));
        assertThat(logRule, recorded(Level.INFO, equalTo("Foo Entry 1"), instanceOf(IllegalStateException.class)));
        assertThat(logRule, not(recorded(Level.INFO, equalTo("Foo Entry 1"), instanceOf(IOException.class))));
    }

    @Test
    public void testRecordedNoShortCircuit() {
        logRule.record("Foo", Level.INFO).capture(2);
        FOO_LOGGER.log(Level.INFO, "Foo Entry", new IllegalStateException());
        FOO_LOGGER.log(Level.INFO, "Foo Entry", new IOException());
        assertThat(logRule, recorded(Level.INFO, equalTo("Foo Entry"), instanceOf(IllegalStateException.class)));
        assertThat(logRule, recorded(Level.INFO, equalTo("Foo Entry"), instanceOf(IOException.class)));
    }

    private boolean active;

    @Test
    public void multipleThreads() throws InterruptedException {
        active = true;
        logRule.record("Foo", Level.INFO).capture(1000);
        Thread thread = new Thread("logging stuff") {
            @Override
            public void run() {
                try {
                    int i = 1;
                    while (active) {
                        FOO_LOGGER.log(Level.INFO, "Foo Entry " + i++);
                        Thread.sleep(50);
                    }
                } catch (InterruptedException x) {
                    // stopped
                }
            }
        };
        try {
            thread.setDaemon(true);
            thread.start();
            Thread.sleep(500);
            for (String message : logRule.getMessages()) {
                assertNotNull(message);
                Thread.sleep(50);
            }
        } finally {
            active = false;
            thread.interrupt();
        }
    }
}
