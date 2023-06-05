/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.console.PlainTextConsoleOutputStream;
import hudson.model.Job;
import hudson.model.Run;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Utility to display the log of a build in real time.
 * Unlike {@link BuildWatcher}, this works well with both {@link RealJenkinsRule} and {@link LocalData}.
 * Use in a {@code try}-with-resources block, typically calling {@link #waitForCompletion} at the end.
 */
@SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "irrelevant")
public final class TailLog implements AutoCloseable {

    private final Semaphore finished = new Semaphore(0);
    private final Tailer tailer;
    private final PrefixedOutputStream.Builder prefixedOutputStreamBuilder = PrefixedOutputStream.builder();

    /**
     * Watch a build already loaded in the current JVM.
     */
    public TailLog(Run<?, ?> b) {
        this(b.getRootDir(), b.getParent().getFullName(), b.getNumber());
    }

    /**
     * Watch a build expected to be loaded in the current JVM.
     * <em>Note</em>: this constructor will not work for a branch project (child of {@code MultiBranchProject}).
     * @param job a {@link Job#getFullName}
     */
    public TailLog(JenkinsRule jr, String job, int number) {
        this(runRootDir(jr.jenkins.getRootDir(), job, number), job, number);
    }

    /**
     * Watch a build expected to be loaded in a controller JVM.
     * <em>Note</em>: this constructor will not work for a branch project (child of {@code MultiBranchProject}).
     * @param job a {@link Job#getFullName}
     */
    public TailLog(RealJenkinsRule rjr, String job, int number) {
        this(runRootDir(rjr.getHome(), job, number), job, number);
    }

    private static File runRootDir(File home, String job, int number) {
        // For MultiBranchProject the last segment would be "branches" not "jobs":
        return new File(home, "jobs/" + job.replace("/", "/jobs/") + "/builds/" + number);
    }

    /**
     * Applies ANSI coloration to log lines produced by this instance.
     * Ignored when on CI.
     * Does not work when the build has already started by the time this method is called.
     */
    public TailLog withColor(PrefixedOutputStream.AnsiColor color) {
        prefixedOutputStreamBuilder.withColor(color);
        return this;
    }

    /**
     * Watch a build expected to run at a specific file location.
     * @param buildDirectory expected {@link Run#getRootDir}
     * @param job a {@link Job#getFullName}
     */
    public TailLog(File buildDirectory, String job, int number) {
        tailer = Tailer.create(new File(buildDirectory, "log"), new TailerListenerAdapter() {
            PrintStream ps;
            @Override
            public void handle(String line) {
                if (ps == null) {
                    ps = new PrintStream(new PlainTextConsoleOutputStream(prefixedOutputStreamBuilder.withName(job + '#' + number).build(System.out)), true, StandardCharsets.UTF_8);
                }
                ps.append(DeltaSupportLogFormatter.elapsedTime());
                ps.print(' ');
                ps.print(line);
                ps.println();
                if (line.startsWith("Finished: ")) {
                    finished.release();
                }
            }
        });
    }

    public void waitForCompletion() throws InterruptedException {
        finished.acquire();
    }

    @Override
    public void close() {
        tailer.stop();
    }

}
