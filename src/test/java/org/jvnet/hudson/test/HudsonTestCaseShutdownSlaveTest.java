/*
 * The MIT License
 *
 * Copyright (c) 2013 IKEDA Yasuyuki
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

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelExpression;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import java.util.concurrent.TimeUnit;

/**
 * Test that agents are cleanly shutdown when test finishes.
 * <p>
 * In Windows, temporary directories fail to be deleted
 * if log files of agents are not closed.
 * This causes failures of tests using HudsonTestCase,
 * for an exception occurs in tearDown().
 * <p>
 * When using JenkinsRule, the exception is squashed in after(),
 * and does not cause failures.
 */
@Issue("JENKINS-18259")
public class HudsonTestCaseShutdownSlaveTest extends HudsonTestCase {
    public void testShutdownSlave() throws Exception {
        DumbSlave agent1 = createOnlineSlave(); // online, and a build finished.
        DumbSlave agent2 = createOnlineSlave(); // online, and a build finished, and disconnected.
        DumbSlave agent3 = createOnlineSlave(); // online, and a build still running.
        DumbSlave agent4 = createOnlineSlave(); // online, and not used.
        DumbSlave agent5 = createSlave(); // offline.

        assertNotNull(agent1);
        assertNotNull(agent2);
        assertNotNull(agent3);
        assertNotNull(agent4);
        assertNotNull(agent5);

        // A build runs on agent1 and finishes.
        {
            FreeStyleProject project1 = createFreeStyleProject();
            project1.setAssignedLabel(LabelExpression.parseExpression(agent1.getNodeName()));
            project1.getBuildersList().add(new SleepBuilder(TimeUnit.SECONDS.toMillis(1)));
            assertBuildStatusSuccess(project1.scheduleBuild2(0));
        }

        // A build runs on agent2 and finishes, then disconnect agent2
        {
            FreeStyleProject project2 = createFreeStyleProject();
            project2.setAssignedLabel(LabelExpression.parseExpression(agent2.getNodeName()));
            project2.getBuildersList().add(new SleepBuilder(TimeUnit.SECONDS.toMillis(1)));
            assertBuildStatusSuccess(project2.scheduleBuild2(0));

            SlaveComputer computer2 = agent2.getComputer();
            computer2.disconnect(null);
            computer2.waitUntilOffline();
        }

        // A build runs on agent3 and does not finish.
        // This build will be interrupted in tearDown().
        {
            FreeStyleProject project3 = createFreeStyleProject();
            project3.setAssignedLabel(LabelExpression.parseExpression(agent3.getNodeName()));
            project3.getBuildersList().add(new SleepBuilder(TimeUnit.MINUTES.toMillis(10)));
            project3.scheduleBuild2(0);
            FreeStyleBuild build;
            while ((build = project3.getLastBuild()) == null) {
                Thread.sleep(TimeUnit.MILLISECONDS.toMillis(500));
            }
            assertTrue(build.isBuilding());
        }
    }
}
