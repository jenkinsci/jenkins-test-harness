package org.jvnet.hudson.test.junit.jupiter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

@WithJenkins
class JenkinsRuleResolverTest {

    @Test
    void jenkinsRuleIsAccessible(JenkinsRule rule) throws IOException {
        assertThat(rule.jenkins.getJobNames(), empty());
        rule.createFreeStyleProject("job-0");
        assertThat(rule.jenkins.getJobNames(), hasSize(1));
    }
}
