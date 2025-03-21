package org.jvnet.hudson.test.junit.jupiter;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.runner.Description;
import java.io.File;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

/**
 * Test {@link JUnit5JenkinsRule}.
 */
@WithJenkins
class JUnit5JenkinsRuleTest {

    @Test
    void restart(JenkinsRule rule) throws Throwable {
        // preserve relevant properties
        URL previousUrl = rule.getURL();
        Description previousTestDescription = rule.getTestDescription();
        File previousRoot = rule.jenkins.getRootDir();

        // create some configuration
        rule.createFreeStyleProject();
        assertThat(rule.jenkins.getJobNames(), hasSize(1));

        // restart the instance with same port and new JENKINS_HOME
        rule.restart();

        // validate properties and configuration were preserved
        assertThat(rule.getURL(), equalTo(previousUrl));
        assertThat(rule.getTestDescription(), equalTo(previousTestDescription));
        assertThat(rule.jenkins.getRootDir(), equalTo(previousRoot));
        assertThat(rule.jenkins.getJobNames(), hasSize(1));

        // validate restarted instance is working
        rule.createFreeStyleProject();
        assertThat(rule.jenkins.getJobNames(), hasSize(2));
    }

}
