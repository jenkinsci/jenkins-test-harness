package org.jvnet.hudson.test.junit.jupiter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class JenkinsRuleResolverWithMethodScopeTest {

	@JenkinsRule
	@Test
	void jenkinsRuleIsAccessible(JenkinsRuleExtension r) throws IOException {
		assertThat(r.jenkins.getJobNames(), empty());
		r.createFreeStyleProject("job-0");
		assertThat(r.jenkins.getJobNames(), hasSize(1));
	}
}
