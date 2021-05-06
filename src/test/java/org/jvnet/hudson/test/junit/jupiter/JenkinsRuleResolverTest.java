package org.jvnet.hudson.test.junit.jupiter;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;

@JenkinsRule
class JenkinsRuleResolverTest {

    @Test
    public void jenkinsRuleIsAccessible(JenkinsRuleExtension r) throws IOException {
        assertThat(r.jenkins.getJobNames(), empty());
        r.createFreeStyleProject("job-0");
        assertThat(r.jenkins.getJobNames(), hasSize(1));
    }

}