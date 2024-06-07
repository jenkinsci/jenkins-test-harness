package org.jvnet.hudson.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

class PluginUtils {

    /**
     * Creates the plugin used by RealJenkinsRule
     * @param destinationDirectory directory to write the plugin to.
     * @param baseline the version of Jenkins to target
     * @throws IOException if something goes wrong whilst creating the plugin.
     * @return File the plugin we just created
     */
    @SuppressFBWarnings(value="PATH_TRAVERSAL_IN", justification = "jth is a test utility, this is package scope code")
    static File createRealJenkinsRulePlugin(File destinationDirectory, String baseline) throws IOException {
        final String pluginName = RealJenkinsRuleInit.class.getSimpleName();

        // The manifest is reused in the plugin and the classes jar.
        Manifest mf = new Manifest();
        Attributes mainAttributes = mf.getMainAttributes();
        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mainAttributes.putValue("Plugin-Class", RealJenkinsRuleInit.class.getName());
        mainAttributes.putValue("Extension-Name", pluginName);
        mainAttributes.putValue("Short-Name", pluginName);
        mainAttributes.putValue("Long-Name", "RealJenkinsRule initialization wrapper");
        mainAttributes.putValue("Plugin-Version", "0-SNAPSHOT (private rjr)");
        mainAttributes.putValue("Support-Dynamic-Loading", "true");
        mainAttributes.putValue("Jenkins-Version", baseline);

        // we need to create a jar for the classes which we can then put into the plugin.
        Path tmpClassesJar = Files.createTempFile("rjr", "jar");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmpClassesJar.toFile());
                    JarOutputStream classesJarOS = new JarOutputStream(fos, mf)) {
                // the actual class
                try (InputStream classIS = RealJenkinsRuleInit.class.getResourceAsStream(RealJenkinsRuleInit.class.getSimpleName() + ".class")) {
                    String path = RealJenkinsRuleInit.class.getPackageName().replace('.', '/');
                    createJarEntry(classesJarOS, path + '/' + RealJenkinsRuleInit.class.getSimpleName() + ".class", classIS);
                }
            }

            // the actual JPI
            File jpi = new File(destinationDirectory, pluginName+".jpi");
            try (FileOutputStream fos = new FileOutputStream(jpi); JarOutputStream jos = new JarOutputStream(fos, mf)) {
                try (FileInputStream fis = new FileInputStream(tmpClassesJar.toFile())) {
                    createJarEntry(jos, "WEB-INF/lib/" + pluginName + ".jar", fis);
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
