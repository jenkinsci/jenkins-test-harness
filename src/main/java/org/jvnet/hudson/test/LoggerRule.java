/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
import java.util.List;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;

/**
 * A test rule which allows you to easily enable one or more loggers for the duration of a test.
 * Call {@link #record(Class, Level)} or another overload for the rule to take effect.
 * <p>By default messages are merely printed to test output.
 * If you also want to examine them, call {@link #capture}.
 * <p>To print and/or capture messages during Jenkins startup,
 * you may compose this with a {@link JenkinsRule} using a {@link RuleChain};
 * or use as a {@link ClassRule}.
 */
public class LoggerRule extends ExternalResource {

    private final LogRecorder recorder = new LogRecorder();

    /**
     * Don't emit logs to the console, only record.
     */
    public LoggerRule quiet() {
        recorder.quiet();
        return this;
    }

    @Override
    public String toString() {
        return recorder.toString();
    }

    /**
     * Initializes log record capture, in addition to merely printing it.
     * This allows you to call {@link #getRecords} and/or {@link #getMessages} later.
     * @param maximum the maximum number of records to keep (any further will be discarded)
     * @return this rule, for convenience
     */
    public LoggerRule capture(int maximum) {
        recorder.capture(maximum);
        return this;
    }

    /**
     * Start listening to a logger.
     * Might be called in a {@link Rule} initializer, to apply to all test cases in a suite;
     * or only at the start of selected test cases.
     * @param logger some logger
     * @param level something between {@link Level#CONFIG} and {@link Level#ALL};
     *              using {@link Level#INFO} or above is typically senseless,
     *              since Java will by default log everything at such levels anyway;
     *              unless you wish to inspect visible {@link #getRecords},
     *              or wish to <em>suppress</em> console log output for some logger
     * @return this rule, for convenience
     */
    public LoggerRule record(Logger logger, Level level) {
        recorder.record(logger, level);
        return this;
    }

    /**
     * Same as {@link #record(Logger, Level)} but calls {@link Logger#getLogger(String)} for you first.
     */
    public LoggerRule record(String name, Level level) {
        recorder.record(Logger.getLogger(name), level);
        return this;
    }

    /**
     * Same as {@link #record(String, Level)} but calls {@link Class#getName()} for you first.
     */
    public LoggerRule record(Class<?> clazz, Level level) {
        recorder.record(clazz.getName(), level);
        return this;
    }

    /**
     * Same as {@link #record(String, Level)} but calls {@link Class#getPackage()} and getName() for you first.
     */
    public LoggerRule recordPackage(Class<?> clazz, Level level) {
        recorder.record(clazz.getPackage().getName(), level);
        return this;
    }

    Map<String, Level> getRecordedLevels() {
        return recorder.getRecordedLevels();
    }

    /**
     * Obtains all log records collected so far during this test case.
     * You must have first called {@link #capture}.
     * If more than the maximum number of records were captured, older ones will have been discarded.
     */
    public List<LogRecord> getRecords() {
        return recorder.getRecords();
    }

    /**
     * Returns a read-only view of current messages.
     *
     * {@link Formatter#formatMessage} applied to {@link #getRecords} at the time of logging.
     * However, if the message is null, but there is an exception, {@link Throwable#toString} will be used.
     * Does not include logger names, stack traces, times, etc. (these will appear in the test console anyway).
     */
    public List<String> getMessages() {
        return recorder.getMessages();
    }

    @Override
    protected void after() {
        recorder.close();
    }

    /**
     * Creates a {@link Matcher} that matches if the {@link LoggerRule} has a {@link LogRecord} at
     * the specified {@link Level}, with a message matching the specified matcher, and with a
     * {@link Throwable} matching the specified matcher.
     * You must have first called {@link #capture}.
     *
     * @param level The {@link Level} of the {@link LoggerRule} to match. Pass {@code null} to match any {@link Level}.
     * @param message the matcher to match against {@link LogRecord#getMessage}
     * @param thrown the matcher to match against {@link LogRecord#getThrown()}. Passing {@code null} is equivalent to
     * passing {@link org.hamcrest.Matchers#anything}
     */
    public static Matcher<LoggerRule> recorded(
            @CheckForNull Level level, @NonNull Matcher<String> message, @CheckForNull Matcher<Throwable> thrown) {
        return new TypeSafeMatcher<>() {

            private final LogRecorder.RecordedMatcher matcher = new LogRecorder.RecordedMatcher(level, message, thrown);

            @Override
            public void describeTo(Description description) {
                matcher.describeTo(description);
            }

            @Override
            protected boolean matchesSafely(LoggerRule loggerRule) {
                return matcher.matches(loggerRule.recorder);
            }
        };
    }

    /**
     * Creates a {@link Matcher} that matches if the {@link LoggerRule} has a {@link LogRecord} at
     * the specified {@link Level} and with a message matching the specified matcher.
     * You must have first called {@link #capture}.
     *
     * @param level The {@link Level} of the {@link LoggerRule} to match. Pass {@code null} to match any {@link Level}.
     * @param message The matcher to match against {@link LogRecord#getMessage}.
     */
    public static Matcher<LoggerRule> recorded(@CheckForNull Level level, @NonNull Matcher<String> message) {
        return recorded(level, message, null);
    }

    /**
     * Creates a {@link Matcher} that matches if the {@link LoggerRule} has a {@link LogRecord}
     * with a message matching the specified matcher and with a {@link Throwable} matching the specified
     * matcher.
     * You must have first called {@link #capture}.
     *
     * @param message the matcher to match against {@link LogRecord#getMessage}
     * @param thrown the matcher to match against {@link LogRecord#getThrown()}. Passing {@code null} is equivalent to
     * passing {@link org.hamcrest.Matchers#anything}
     */
    public static Matcher<LoggerRule> recorded(
            @NonNull Matcher<String> message, @CheckForNull Matcher<Throwable> thrown) {
        return recorded(null, message, thrown);
    }

    /**
     * Creates a {@link Matcher} that matches if the {@link LoggerRule} has a {@link LogRecord}
     * with a message matching the specified matcher.
     * You must have first called {@link #capture}.
     *
     * @param message the matcher to match against {@link LogRecord#getMessage}
     */
    public static Matcher<LoggerRule> recorded(@NonNull Matcher<String> message) {
        return recorded(null, message, null);
    }
}
