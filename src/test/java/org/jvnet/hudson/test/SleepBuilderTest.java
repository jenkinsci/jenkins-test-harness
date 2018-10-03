package org.jvnet.hudson.test;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;

public class SleepBuilderTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testPerform() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        SleepBuilder builder = new SleepBuilder(30);
        project.getBuildersList().add(builder);
        j.configRoundtrip(project);
        j.assertEqualDataBoundBeans(project.getBuildersList().get(SleepBuilder.class), builder);
    }

}
