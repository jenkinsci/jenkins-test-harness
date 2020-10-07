package org.jvnet.hudson.test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

import java.io.File;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.jvnet.hudson.test.recipes.WithPluginManager;
import org.kohsuke.stapler.HttpResponse;

import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.util.IOUtils;

import jenkins.model.Jenkins;

public class RestartableJenkinsRuleTest {

    @Rule public RestartableJenkinsRule noPortReuse = new RestartableJenkinsRule();

    @Rule
    public RestartableJenkinsRule portReuse =
            new RestartableJenkinsRule.Builder().withReusedPort().build();

    @Test
    public void testNoPortReuse() throws Exception {
        assumeThat(
                "This test requires a custom port to not be set.",
                System.getProperty("port"),
                nullValue());

        AtomicInteger port = new AtomicInteger();
        noPortReuse.then(
                s -> {
                    port.set(noPortReuse.j.getURL().getPort());
                });
        noPortReuse.then(
                s -> {
                    assertNotEquals(port.get(), noPortReuse.j.getURL().getPort());
                });
    }

    @Test
    public void testPortReuse() throws Exception {
        assumeThat(
                "This test requires a custom port to not be set.",
                System.getProperty("port"),
                nullValue());

        AtomicInteger port = new AtomicInteger();
        portReuse.then(
                s -> {
                    port.set(portReuse.j.getURL().getPort());
                });
        portReuse.then(
                s -> {
                    assertEquals(port.get(), portReuse.j.getURL().getPort());
                });
    }

    @Test
    @WithPluginManager(UnitTestSupportingPluginManager.class)
    public void pluginsCanBeDisabled() throws Exception {
        final String pluginId = "display-url-api";
        noPortReuse.then(jr -> {
            System.out.println(WarExploder.getExplodedDir());
            Path srcLdap = new File(WarExploder.getExplodedDir(), "WEB-INF/detached-plugins/"+pluginId+".hpi").toPath();
            Path dstLdap = new File(jr.jenkins.pluginManager.rootDir, pluginId+".jpi").toPath();
            Files.createDirectories(dstLdap.getParent());
            Files.copy(srcLdap, dstLdap);
            //jr.getPluginManager().doCheckUpdatesServer();
            //jr.getPluginManager().install(Collections.singletonList("cvs"),true);
        });
        
        noPortReuse.then(jr -> {
            Jenkins j = jr.jenkins;
            PluginManager pm = j.getPluginManager();
            PluginWrapper plugin = pm.getPlugin(pluginId);
            assertNotNull("plugin should not be null", plugin);
            plugin.disable();
        });

        noPortReuse.then(jr -> {
            assertFalse(pluginId + " is not enabled",
                        jr.jenkins.getPluginManager().getPlugin(pluginId).isEnabled());
            assertFalse(pluginId + " should not be active",
                        jr.jenkins.getPluginManager().getPlugin(pluginId).isActive());
        });
    }
}
