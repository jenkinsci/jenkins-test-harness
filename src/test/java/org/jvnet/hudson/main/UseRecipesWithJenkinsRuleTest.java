package org.jvnet.hudson.main;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import hudson.LocalPluginManager;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.JenkinsLocationConfiguration;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.recipes.LocalData;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.jvnet.hudson.test.recipes.WithPluginManager;
import org.xml.sax.SAXException;

public class UseRecipesWithJenkinsRuleTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    @LocalData
    public void testGetItemFromLocalData() {
        assertNotNull(rule.jenkins.getItem("testJob"));
    }

    @Test
    @LocalData
    public void testGetItemFromLocalDataZip() {
        assertNotNull(rule.jenkins.getItem("somejob"));
    }

    @Test
    @WithPlugin("keep-slave-disconnected.jpi")
    public void testWithPlugin() {
        assertNotNull(rule.jenkins.getPlugin("keep-slave-disconnected"));
    }

    @Test
    @PresetData(DataSet.ANONYMOUS_READONLY)
    public void testPresetData() throws Exception {
        WebClient wc = rule.createWebClient();
        wc.assertFails("loginError", HttpServletResponse.SC_UNAUTHORIZED);
        // but not once the user logs in.
        verifyNotError(wc.login("alice"));
    }

    @Test
    @WithPluginManager(MyPluginManager.class)
    public void testWithPluginManager() {
        assertEquals(MyPluginManager.class, rule.jenkins.pluginManager.getClass());
    }

    @Test public void rightURL() throws Exception {
        assertEquals(rule.getURL(), new URL(JenkinsLocationConfiguration.get().getUrl()));
    }

    private void verifyNotError(WebClient wc) throws IOException, SAXException {
        HtmlPage p = wc.goTo("loginError");
        URL url = p.getUrl();
        System.out.println(url);
        assertThat(url.toExternalForm(), not(containsString("login")));
    }

    public static class MyPluginManager extends LocalPluginManager {
        public MyPluginManager(File rootDir) {
            super(rootDir);
        }
    }
}
