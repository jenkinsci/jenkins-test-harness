package org.jvnet.hudson.test.junit.jupiter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

@WithJenkins
public class WithLocalDataTest {
    @WithLocalData
    @Test
    void works(JenkinsRule r) {
        Assertions.assertNotNull(r.jenkins.getItem("somejob"));
    }

    @WithLocalData
    @Test
    void methodData(JenkinsRule r) {
        Assertions.assertEquals("This is Jenkins in WithLocalDataTest#methodData", r.jenkins.getSystemMessage());
    }

    @WithLocalData("methodData")
    @Test
    void otherData(JenkinsRule r) {
        Assertions.assertEquals("This is Jenkins in WithLocalDataTest#methodData", r.jenkins.getSystemMessage());
    }
}
