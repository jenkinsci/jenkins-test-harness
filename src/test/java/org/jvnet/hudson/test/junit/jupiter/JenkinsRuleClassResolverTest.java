package org.jvnet.hudson.test.junit.jupiter;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;

@WithJenkins
class JenkinsRuleClassResolverTest {

    static JenkinsRule rule;

    @Test
    void jenkinsRuleIsAccessible() throws IOException {
        assertThat(rule.jenkins.getJobNames(), empty());
        rule.createFreeStyleProject("job-0");
        assertThat(rule.jenkins.getJobNames(), hasSize(1));
    }
}
