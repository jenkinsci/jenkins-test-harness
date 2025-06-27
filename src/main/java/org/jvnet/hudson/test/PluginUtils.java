package org.jvnet.hudson.test;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class PluginUtils {

    /**
     * Creates the plugin used by RealJenkinsExtension
     * @param destinationDirectory directory to write the plugin to.
     * @param baseline the version of Jenkins to target
     * @throws IOException if something goes wrong whilst creating the plugin.
     * @return File the plugin we just created
     */
    public static File createRealJenkinsExtensionPlugin(File destinationDirectory, String baseline) throws IOException {
        return createRealJenkinsPlugin("RealJenkinsExtension", destinationDirectory,  baseline);
    }

    /**
     * Creates the plugin used by RealJenkinsRule
     * @param destinationDirectory directory to write the plugin to.
     * @param baseline the version of Jenkins to target
     * @throws IOException if something goes wrong whilst creating the plugin.
     * @return File the plugin we just created
     */
    static File createRealJenkinsRulePlugin(File destinationDirectory, String baseline) throws IOException {
        return createRealJenkinsPlugin("RealJenkinsRule", destinationDirectory,  baseline);
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "jth is a test utility, this is package scope code")
    static File createRealJenkinsPlugin(String target, File destinationDirectory, String baseline) throws IOException {
        Class<RealJenkinsRuleInit> pluginClass = RealJenkinsRuleInit.class;

        // The manifest is reused in the plugin and the classes jar.
        Manifest mf = new Manifest();
        Attributes mainAttributes = mf.getMainAttributes();
        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mainAttributes.putValue("Plugin-Class", pluginClass.getName());
        mainAttributes.putValue("Extension-Name", pluginClass.getSimpleName());
        mainAttributes.putValue("Short-Name", pluginClass.getSimpleName());
        mainAttributes.putValue("Long-Name", target + " initialization wrapper");
        mainAttributes.putValue("Plugin-Version", "0-SNAPSHOT (private rj)");
        mainAttributes.putValue("Support-Dynamic-Loading", "true");
        mainAttributes.putValue("Jenkins-Version", baseline);
        mainAttributes.putValue("Init-Target", target);

        // we need to create a jar for the classes which we can then put into the plugin.
        Path tmpClassesJar = Files.createTempFile("rjr", "jar");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmpClassesJar.toFile());
                 JarOutputStream classesJarOS = new JarOutputStream(fos, mf)) {
                // the actual class
                try (InputStream classIS = pluginClass.getResourceAsStream(pluginClass.getSimpleName() + ".class")) {
                    String path = pluginClass.getPackageName().replace('.', '/');
                    createJarEntry(classesJarOS, path + '/' + pluginClass.getSimpleName() + ".class", classIS);
                }
            }

            // the actual JPI
            File jpi = new File(destinationDirectory, pluginClass.getSimpleName() + ".jpi");
            try (FileOutputStream fos = new FileOutputStream(jpi); JarOutputStream jos = new JarOutputStream(fos, mf)) {
                try (FileInputStream fis = new FileInputStream(tmpClassesJar.toFile())) {
                    createJarEntry(jos, "WEB-INF/lib/" + pluginClass.getSimpleName() + ".jar", fis);
                }
            }
            return jpi;
        } finally {
            Files.delete(tmpClassesJar);
        }
    }

    private static void createJarEntry(JarOutputStream jos, String entryName, InputStream data) throws IOException {
        JarEntry je = new JarEntry(entryName);
        jos.putNextEntry(je);
        data.transferTo(jos);
        jos.closeEntry();
    }
}
