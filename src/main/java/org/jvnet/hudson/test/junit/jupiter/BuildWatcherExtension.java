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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.console.LineTransformationOutputStream;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.DeltaSupportLogFormatter;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TailLog;

/**
 * Echoes build output to standard error as it arrives.
 * Usage: <pre>{@code
 * @RegisterExtension
 * private static final BuildWatcherExtension buildWatcher = new BuildWatcherExtension();
 * }</pre>
 * Should work in combination with {@link JenkinsRule} or {@link JenkinsSessionExtension}.
 * <p>
 * This is the JUnit5 implementation of {@link BuildWatcher}.
 *
 * @see JenkinsRule#waitForCompletion
 * @see JenkinsRule#waitForMessage
 * @see TailLog
 * @see BuildWatcher
 */
public final class BuildWatcherExtension implements BeforeAllCallback, AfterAllCallback {

    private static boolean active;
    private static final Map<File, RunningBuild> builds = new ConcurrentHashMap<>();

    private Thread thread;

    @Override
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        active = true;
        thread = new Thread("watching builds") {
            @Override
            public void run() {
                try {
                    while (active) {
                        for (RunningBuild build : builds.values()) {
                            build.copy();
                        }
                        Thread.sleep(50);
                    }
                } catch (InterruptedException x) {
                    // stopped
                }
                // last chance
                for (RunningBuild build : builds.values()) {
                    build.copy();
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        active = false;
        thread.interrupt();
    }

    @Extension
    public static final class Listener extends RunListener<Run<?, ?>> {

        @Override
        public void onStarted(Run<?, ?> r, TaskListener listener) {
            if (!active) {
                return;
            }
            RunningBuild build = new RunningBuild(r);
            RunningBuild orig = builds.put(r.getRootDir(), build);
            if (orig != null) {
                System.err.println(r + " was started twice?!");
            }
        }

        @Override
        public void onFinalized(Run<?, ?> r) {
            if (!active) {
                return;
            }
            RunningBuild build = builds.remove(r.getRootDir());
            if (build != null) {
                build.copy();
            } else {
                System.err.println(
                        r + " was finalized but never started; assuming it was started earlier using @LocalData");
                new RunningBuild(r).copy();
            }
        }
    }

    private static final class RunningBuild {

        private final Run<?, ?> r;
        private final OutputStream sink;
        private long pos;

        RunningBuild(Run<?, ?> r) {
            this.r = r;
            sink = new LogLinePrefixOutputFilter(System.err, "[" + r + "] ");
        }

        synchronized void copy() {
            try {
                pos = r.getLogText().writeLogTo(pos, sink);
                // Note that !log.isComplete() after the initial call to copy, even if the build is complete, because
                // Run.getLogText never calls markComplete!
                // That is why Run.writeWholeLogTo calls getLogText repeatedly.
                // Even if it did call markComplete this might not work from RestartableJenkinsRule since you would have
                // a different Run object after the restart.
                // Anyway we can just rely on onFinalized to let us know when to stop.
            } catch (FileNotFoundException x) {
                // build deleted or not started
            } catch (Throwable x) {
                if (Jenkins.getInstanceOrNull() != null) {
                    x.printStackTrace();
                } else {
                    // probably just IllegalStateException: Jenkins.instance is missing, AssertionError: class … is
                    // missing its descriptor, etc.
                }
            }
        }
    }

    // Copied from WorkflowRun.
    private static final class LogLinePrefixOutputFilter extends LineTransformationOutputStream {

        private final PrintStream logger;
        private final String prefix;

        LogLinePrefixOutputFilter(PrintStream logger, String prefix) {
            this.logger = logger;
            this.prefix = prefix;
        }

        @Override
        protected void eol(byte[] b, int len) throws IOException {
            logger.append(DeltaSupportLogFormatter.elapsedTime());
            logger.write(' ');
            logger.append(prefix);
            logger.write(b, 0, len);
        }
    }
}
