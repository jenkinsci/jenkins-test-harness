package org.jvnet.hudson.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

class PluginUtilsTest {

    @Test
    void testPluginUpdating(@TempDir File tmpDir) throws IOException {
        File pluginDst = new File(tmpDir, "plugin-update-test.jpi");

        try (InputStream resourceAsStream = RealJenkinsRule.class.getResourceAsStream("RealJenkinsRuleInit.jpi");
                JarInputStream jis = new JarInputStream(resourceAsStream)) {
            PluginUtils.updateMinimumJenkinsVersion(jis, pluginDst, "3.6666");
        }
        try (JarFile updatedPlugin = new JarFile(pluginDst)) {
            assertThat(updatedPlugin.getManifest().getMainAttributes(), hasEntry(hasToString("Jenkins-Version"), is("3.6666")));
        }
    }

}
