package org.jvnet.hudson.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import org.hamcrest.Matchers;
import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

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
