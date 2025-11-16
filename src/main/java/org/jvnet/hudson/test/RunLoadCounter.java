/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.RunAction2;
import jenkins.model.lazy.LazyBuildMixIn;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Utility to determine when a build record is loaded.
 * @since 1.517
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // API design mistakes
public final class RunLoadCounter {

    private static final Logger LOGGER = Logger.getLogger(RunLoadCounter.class.getName());

    private static LazyBuildMixIn.LazyLoadingJob<?, ?> currProject;
    private static int currCount, maxCount;

    /**
     * @deprecated No longer needed.
     */
    @Deprecated
    public static void prepare(LazyBuildMixIn.LazyLoadingJob<?, ?> project) throws IOException {}

    /**
     * Counts how many build records are loaded as a result of some task.
     * @param project a project which may have some builds
     * @param thunk a task which is expected to load some build records
     * @return how many build records were actually {@linkplain Run#onLoad loaded} as a result
     */
    public static int countLoads(LazyBuildMixIn.LazyLoadingJob<?, ?> project, Runnable thunk) {
        MarkerAdder.register(project);
        project.getLazyBuildMixIn()._getRuns().purgeCache();
        currProject = project;
        currCount = 0;
        maxCount = Integer.MAX_VALUE;
        try {
            LOGGER.info("Starting load count");
            thunk.run();
            return currCount;
        } finally {
            LOGGER.info("Ending load count");
            currProject = null;
        }
    }

    /**
     * Asserts that at most a certain number of build records are loaded as a result of some task.
     * @param project a project which may have some builds
     * @param max the maximum number of build records we expect to load
     * @param thunk a task which is expected to load some build records
     * @return the result of the task, if any
     * @throws Exception if the task failed
     * @throws AssertionError if one more than max build record is loaded
     * @param <T> the return value type
     */
    public static <T> T assertMaxLoads(LazyBuildMixIn.LazyLoadingJob<?, ?> project, int max, Callable<T> thunk)
            throws Exception {
        MarkerAdder.register(project);
        project.getLazyBuildMixIn()._getRuns().purgeCache();
        currProject = project;
        currCount = 0;
        maxCount = max;
        try {
            LOGGER.info("Starting load count");
            return thunk.call();
        } finally {
            LOGGER.info("Ending load count");
            currProject = null;
        }
    }

    private RunLoadCounter() {}

    /**
     * Used internally.
     */
    @Restricted(NoExternalUse.class)
    public static final class Marker extends InvisibleAction implements RunAction2 {

        @Override
        public void onLoad(Run<?, ?> run) {
            if (run.getParent().equals(currProject)) {
                if (++currCount > maxCount) {
                    throw new AssertionError("More than " + maxCount + " build records loaded: " + run);
                } else {
                    LOGGER.log(
                            Level.WARNING,
                            "Loaded " + run + " (" + currCount + " ≤ " + maxCount + ")",
                            new Throwable());
                }
            }
        }

        @Override
        public void onAttached(Run r) {
            LOGGER.info(() -> "Registered on " + r);
        }
    }

    /**
     * Used internally.
     */
    @Restricted(NoExternalUse.class)
    @Extension
    public static final class MarkerAdder extends RunListener<Run<?, ?>> {
        private final Set<String> jobs = new HashSet<>();

        static void register(LazyBuildMixIn.LazyLoadingJob<?, ?> job) {
            if (ExtensionList.lookupSingleton(MarkerAdder.class).jobs.add(((Job) job).getFullName())) {
                for (Run<?, ?> build : job.getLazyBuildMixIn()._getRuns()) {
                    build.addAction(new Marker());
                    try {
                        build.save();
                    } catch (IOException x) {
                        throw new RuntimeException(x);
                    }
                }
            }
        }

        @Override
        public void onStarted(Run<?, ?> build, TaskListener listener) {
            if (jobs.contains(build.getParent().getFullName())) {
                build.addAction(new Marker());
            }
        }
    }
}
