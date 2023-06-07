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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;

public class LoggerRuleTest {

    @Rule
    public LoggerRule logRule = new LoggerRule();

    private static final Logger FOO_LOGGER = Logger.getLogger("Foo");
    private static final Logger BAR_LOGGER = Logger.getLogger("Bar");

    @Test
    public void testRecordedSingleLogger() {
        logRule.record("Foo", Level.INFO).capture(1);
        FOO_LOGGER.log(Level.INFO, "Entry 1");
        assertThat(logRule, LoggerRule.recorded(Level.INFO, equalTo("Entry 1")));
        assertThat(logRule, not(LoggerRule.recorded(Level.WARNING, equalTo("Entry 1"))));
        FOO_LOGGER.log(Level.INFO, "Entry 2");
        assertThat(logRule, not(LoggerRule.recorded(equalTo("Entry 1"))));
        assertThat(logRule, LoggerRule.recorded(equalTo("Entry 2")));
    }

    @Test
    public void assertionErrorMatchesExpectedText() {
        logRule.record("Foo", Level.INFO).capture(2);
        FOO_LOGGER.log(Level.INFO, "Entry 1");
        FOO_LOGGER.log(Level.INFO, "Entry 3");
        AssertionError assertionError = assertThrows(AssertionError.class, () -> assertThat(logRule, LoggerRule.recorded(Level.INFO, equalTo("Entry 2"))));

        assertThat(assertionError.getMessage(), containsString("Expected: has LogRecord with level \"INFO\" with a message matching \"Entry 2\""));
        assertThat(assertionError.getMessage(), containsString("     but: was <INFO->Entry 3,INFO->Entry 1>"));
    }

    @Test
    public void testRecordedMultipleLoggers() {
        logRule.record("Foo", Level.INFO).record("Bar", Level.SEVERE).capture(2);
        FOO_LOGGER.log(Level.INFO, "Foo Entry 1");
        BAR_LOGGER.log(Level.SEVERE, "Bar Entry 1");
        assertThat(logRule, LoggerRule.recorded(equalTo("Foo Entry 1")));
        assertThat(logRule, LoggerRule.recorded(equalTo("Bar Entry 1")));
        // All criteria must match a single LogRecord.
        assertThat(logRule, not(LoggerRule.recorded(Level.INFO, equalTo("Bar Entry 1"))));
    }

    @Test
    public void testRecordedThrowable() {
        logRule.record("Foo", Level.INFO).capture(1);
        FOO_LOGGER.log(Level.INFO, "Foo Entry 1", new IllegalStateException());
        assertThat(logRule, LoggerRule.recorded(equalTo("Foo Entry 1"), instanceOf(IllegalStateException.class)));
        assertThat(logRule, LoggerRule.recorded(Level.INFO, equalTo("Foo Entry 1"), instanceOf(IllegalStateException.class)));
        assertThat(logRule, not(LoggerRule.recorded(Level.INFO, equalTo("Foo Entry 1"), instanceOf(IOException.class))));
    }

    @Test
    public void testRecordedNoShortCircuit() {
        logRule.record("Foo", Level.INFO).capture(2);
        FOO_LOGGER.log(Level.INFO, "Foo Entry", new IllegalStateException());
        FOO_LOGGER.log(Level.INFO, "Foo Entry", new IOException());
        assertThat(logRule, LoggerRule.recorded(Level.INFO, equalTo("Foo Entry"), instanceOf(IllegalStateException.class)));
        assertThat(logRule, LoggerRule.recorded(Level.INFO, equalTo("Foo Entry"), instanceOf(IOException.class)));
    }

    @Test
    public void multipleThreads() throws InterruptedException {
        AtomicBoolean active = new AtomicBoolean(true);
        logRule.record("Foo", Level.INFO).capture(1000);
        Thread thread = new Thread("logging stuff") {
            @Override
            public void run() {
                try {
                    int i = 1;
                    while (active.get()) {
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
            active.set(false);
            thread.interrupt();
        }
    }
}
