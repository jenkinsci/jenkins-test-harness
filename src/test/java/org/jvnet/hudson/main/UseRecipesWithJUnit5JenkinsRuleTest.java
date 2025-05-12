package org.jvnet.hudson.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.LocalPluginManager;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.jvnet.hudson.test.recipes.WithPluginManager;

/**
 * Test compatibility of recipes with JUnit5 JenkinsRule.
 * See {@link UseRecipesWithJenkinsRuleTest}.
 */
@WithJenkins
class UseRecipesWithJUnit5JenkinsRuleTest {

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        this.rule = rule;
    }

    @Test
    @LocalData
    void testGetItemFromLocalData() {
        assertNotNull(rule.jenkins.getItem("testJob"));
    }

    @Test
    @LocalData
    void testGetItemFromLocalDataZip() {
        assertNotNull(rule.jenkins.getItem("somejob"));
    }

    @Test
    @WithPlugin("keep-slave-disconnected.jpi")
    void testWithPlugin() {
        assertNotNull(rule.jenkins.getPlugin("keep-slave-disconnected"));
    }

    @Test
    @WithPluginManager(MyPluginManager.class)
    void testWithPluginManager() {
        assertEquals(MyPluginManager.class, rule.jenkins.pluginManager.getClass());
    }

    public static class MyPluginManager extends LocalPluginManager {
        public MyPluginManager(File rootDir) {
            super(rootDir);
        }
    }
}
