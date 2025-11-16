package org.jvnet.hudson.test.junit.jupiter;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class JenkinsRuleResolverBeforeTest {
    private JenkinsRule rule;

    @BeforeEach
    void before(JenkinsRule rule) {
        this.rule = rule;
    }

    @LocalData
    @Test
    void localData() {
        assertNotNull(rule.jenkins.getItem("testJob"));
    }

    @LocalData
    @Test
    void localDataZip() {
        assertNotNull(rule.jenkins.getItem("somejob"));
    }
}
