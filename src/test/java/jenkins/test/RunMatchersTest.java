/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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
package jenkins.test;

import static jenkins.test.RunMatchers.completed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static jenkins.test.RunMatchers.logContains;
import static jenkins.test.RunMatchers.hasStatus;
import static jenkins.test.RunMatchers.isSuccessful;

import hudson.Functions;
import hudson.model.Result;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;

public class RunMatchersTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void buildSuccessful() throws Exception {
        var p = j.createFreeStyleProject();
        p.getBuildersList().add(new SleepBuilder(1000));
        var b = p.scheduleBuild2(0).waitForStart();
        assertThat(j.waitForCompletion(b), allOf(completed(), isSuccessful()));
    }

    @Test
    public void buildFailure() throws Exception {
        var p = j.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        var b = p.scheduleBuild2(0).waitForStart();
        assertThat(j.waitForCompletion(b), hasStatus(Result.FAILURE));
    }

    @Test
    public void assertThatLogContains() throws Exception {
        var p = j.createFreeStyleProject();
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo hello") : new Shell("echo hello"));
        var b = p.scheduleBuild2(0).get();
        System.out.println(b.getDisplayName() + " completed");
        assertThat(b, allOf(logContains("echo hello"), not(logContains("echo bye"))));
    }
}
