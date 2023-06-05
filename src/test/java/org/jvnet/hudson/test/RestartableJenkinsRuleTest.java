package org.jvnet.hudson.test;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeThat;

import hudson.PluginManager;
import hudson.PluginWrapper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.recipes.WithPluginManager;

public class RestartableJenkinsRuleTest {

    @Rule
    public RestartableJenkinsRule noPortReuse = new RestartableJenkinsRule();

    @Rule
    public RestartableJenkinsRule portReuse =
            new RestartableJenkinsRule.Builder().withReusedPort().build();

    @Test
    public void testNoPortReuse() {
        assumeThat(
                "This test requires a custom port to not be set.",
                System.getProperty("port"),
                nullValue());

        AtomicInteger port = new AtomicInteger();
        noPortReuse.then(
                s -> port.set(noPortReuse.j.getURL().getPort()));
        noPortReuse.then(
                s -> assertNotEquals(port.get(), noPortReuse.j.getURL().getPort()));
    }

    @Test
    public void testPortReuse() {
        assumeThat(
                "This test requires a custom port to not be set.",
                System.getProperty("port"),
                nullValue());

        AtomicInteger port = new AtomicInteger();
        portReuse.then(
                s -> port.set(portReuse.j.getURL().getPort()));
        portReuse.then(
                s -> assertEquals(port.get(), portReuse.j.getURL().getPort()));
    }

    @Test
    @WithPluginManager(UnitTestSupportingPluginManager.class)
    public void pluginsCanBeDisabled() {
        final String pluginId = "display-url-api";
        noPortReuse.then(jr -> {
            System.out.println(WarExploder.getExplodedDir());
            Path srcLdap = new File(WarExploder.getExplodedDir(), "WEB-INF/detached-plugins/" + pluginId + ".hpi").toPath();
            Path dstLdap = new File(jr.jenkins.pluginManager.rootDir, pluginId + ".jpi").toPath();
            Files.createDirectories(dstLdap.getParent());
            Files.copy(srcLdap, dstLdap);
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

    @Test
    public void verify_CopyFileVisitor_visitFileFailed_NoSuchFileException() {
        Path testPath = new File("./").toPath();
        RestartableJenkinsRule.CopyFileVisitor visitor = new RestartableJenkinsRule.CopyFileVisitor(testPath);

        assertEquals(FileVisitResult.CONTINUE, visitor.visitFileFailed(testPath, new FileNotFoundException()));
        assertEquals(FileVisitResult.CONTINUE, visitor.visitFileFailed(testPath, new NoSuchFileException("./")));
        assertEquals(FileVisitResult.TERMINATE, visitor.visitFileFailed(testPath, new IOException()));
    }
}

