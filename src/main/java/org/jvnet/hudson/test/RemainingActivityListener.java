/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Queue;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Checks for any ongoing activity (queue, builds) that might interfere with {@link TemporaryDirectoryAllocator#dispose}.
 */
@Extension
public final class RemainingActivityListener implements EndOfTestListener {

    /**
     * Set to true if you wish for warnings to be treated as fatal test errors.
     */
    public static boolean fatal = Boolean.getBoolean(RemainingActivityListener.class.getName() + ".fatal");

    @Override
    public void onTearDown() throws Exception {
        for (Computer c : Jenkins.get().getComputers()) {
            for (Executor x : c.getAllExecutors()) {
                if (!x.isIdle()) {
                    problem(x.getCurrentExecutable() + " still seems to be running, which could break deletion of log files or metadata");
                }
            }
        }
        for (Queue.Item q : Queue.getInstance().getItems()) {
            problem(q + " is still scheduled, which if it ever runs, could break deletion of log files or metadata");
        }
    }

    private static void problem(String message) {
        if (fatal) {
            throw new AssertionError(message);
        } else {
            Logger.getLogger(RemainingActivityListener.class.getName()).warning(message);
        }
    }

}
