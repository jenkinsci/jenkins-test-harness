package org.jvnet.hudson.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Logger;

class PluginUtils {

    private static final Logger LOGGER = Logger.getLogger(PluginUtils.class.getName());

    /**
     * Update a plugin's declared Jenkins version to the specified version
     * @param pluginSrc the source of the plugin
     * @param pluginDst the location to write the updated plugin to
     * @param baseline the baseline to set of the plugin
     * @throws IOException if there was an error whilst attempting to update the plugins baseline
     */
    static void updateMinimumJenkinsVersion(JarInputStream pluginSrc, File pluginDst, String baseline) throws IOException {
        Manifest manifest = pluginSrc.getManifest();
        Attributes mainAttributes = manifest.getMainAttributes();
        String oldBaseline = mainAttributes.getValue("Jenkins-Version");
        String pluginName = mainAttributes.getValue("Short-Name");

        LOGGER.info(() -> "updating jenkins baseline for " + pluginName + " from " + oldBaseline + " to " + baseline);
        mainAttributes.putValue("Jenkins-Version", baseline.toString());

        try (FileOutputStream fos = new FileOutputStream(pluginDst); JarOutputStream jos = new JarOutputStream(fos, manifest)) {
            for (JarEntry je = pluginSrc.getNextJarEntry(); je != null; je = pluginSrc.getNextJarEntry()) {
                jos.putNextEntry(je);
                pluginSrc.transferTo(jos);
                jos.closeEntry();
            }
        }
    }
}
