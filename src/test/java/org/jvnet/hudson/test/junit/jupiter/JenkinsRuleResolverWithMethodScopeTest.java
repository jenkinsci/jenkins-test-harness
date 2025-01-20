package org.jvnet.hudson.test.junit.jupiter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

class JenkinsRuleResolverWithMethodScopeTest {

    @Test
    @WithJenkins
    void jenkinsRuleIsAccessible(JenkinsRule rule) throws IOException {
        assertThat(rule.jenkins.getJobNames(), empty());
        rule.createFreeStyleProject("job-0");
        assertThat(rule.jenkins.getJobNames(), hasSize(1));
    }

    @Test
    @LocalData
    @WithJenkins
    void jenkinsRuleUsesLocalData(JenkinsRule rule) {
        assertNotNull(rule.jenkins.getItem("testJob"));
    }

    @Test
    @LocalData
    @WithJenkins
    void jenkinsRuleUsesLocalDataZip(JenkinsRule rule) {
        assertNotNull(rule.jenkins.getItem("somejob"));
    }
}
