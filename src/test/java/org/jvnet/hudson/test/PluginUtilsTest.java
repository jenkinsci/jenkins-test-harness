package org.jvnet.hudson.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginUtilsTest {

    @Test
    void createRealJenkinsRulePlugin(@TempDir File tmpDir) throws IOException {
        File plugin = PluginUtils.createRealJenkinsRulePlugin(tmpDir, "3.6666");
        assertThat(plugin, FileMatchers.anExistingFile());
        try (JarFile hpi = new JarFile(plugin)) {
            assertThat(hpi.getManifest().getMainAttributes(), hasEntry(hasToString("Jenkins-Version"), is("3.6666")));
        }
    }
}
