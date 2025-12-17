package org.jvnet.hudson.test.injected;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Base class for tests injected via the <code>maven-hpi-plugin</code>.
 * All injected tests should extend this class in order to be discovered by the <code>maven-hpi-plugin</code> in order to access the provided configuration parameters.
 */
@ExtendWith(InjectedTest.ExtensionContextResolver.class)
public abstract class InjectedTest {

    static String basedir;
    static String groupId;
    static String artifactId;
    static String version;
    static String packaging;
    static String outputDirectory;
    static String testOutputDirectory;
    static boolean requirePI;

    /**
     * Common initializer for injected tests.
     * Parses the configuration parameters from the given {@link ExtensionContext}.
     *
     * @param context the extension context injected by {@link ExtensionContextResolver}
     */
    @BeforeAll
    static void beforeAll(ExtensionContext context) {
        basedir = context.getConfigurationParameter("InjectedTest.basedir")
                .orElseThrow(
                        () -> new IllegalArgumentException("Missing configuration value for 'InjectedTest.basedir"));
        groupId = context.getConfigurationParameter("InjectedTest.groupId")
                .orElseThrow(
                        () -> new IllegalArgumentException("Missing configuration value for 'InjectedTest.groupId"));
        artifactId = context.getConfigurationParameter("InjectedTest.artifactId")
                .orElseThrow(
                        () -> new IllegalArgumentException("Missing configuration value for 'InjectedTest.artifactId"));
        version = context.getConfigurationParameter("InjectedTest.version")
                .orElseThrow(
                        () -> new IllegalArgumentException("Missing configuration value for 'InjectedTest.version"));
        packaging = context.getConfigurationParameter("InjectedTest.packaging")
                .orElseThrow(
                        () -> new IllegalArgumentException("Missing configuration value for 'InjectedTest.packaging"));
        outputDirectory = context.getConfigurationParameter("InjectedTest.outputDirectory")
                .orElseThrow(() ->
                        new IllegalArgumentException("Missing configuration value for 'InjectedTest.outputDirectory"));
        testOutputDirectory = context.getConfigurationParameter("InjectedTest.testOutputDirectory")
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing configuration value for 'InjectedTest.testOutputDirectory"));
        requirePI = Boolean.parseBoolean(context.getConfigurationParameter("InjectedTest.requirePI")
                .orElseThrow(
                        () -> new IllegalArgumentException("Missing configuration value for 'InjectedTest.requirePI")));

        System.out.println(
                "Running InjectedTest." + context.getTestClass().orElseThrow().getSimpleName() + " for " + groupId + ":"
                        + artifactId + ":" + version);
    }

    /**
     * Scans the given resource for files with the given extension.
     *
     * @param resource the resource to scan - can be a directory or jar file.
     * @param extension the file extension
     * @return a map containing the {@link URL} to a resource as value and the string representation as key
     * @throws IOException for errors when scanning the given resource
     */
    static Map<String, URL> scan(File resource, String extension) throws IOException {
        Map<String, URL> result = new HashMap<>();
        if (resource.isDirectory()) {
            for (File f : FileUtils.listFiles(resource, new String[] {extension}, true)) {
                result.put(
                        f.getAbsolutePath().substring((resource.getAbsolutePath() + File.separator).length()),
                        f.toURI().toURL());
            }
        } else if (resource.getName().endsWith(".jar")) {
            String jarUrl = resource.toURI().toURL().toExternalForm();
            try (JarFile jf = new JarFile(resource)) {
                Enumeration<JarEntry> e = jf.entries();
                while (e.hasMoreElements()) {
                    JarEntry ent = e.nextElement();
                    if (ent.getName().endsWith("." + extension)) {
                        result.put(ent.getName(), new URL("jar:" + jarUrl + "!/" + ent.getName()));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Resolves the {@link ExtensionContext} containing configuration values provided by <code>maven-hpi-plugin</code> for injected tests.
     */
    public static class ExtensionContextResolver implements ParameterResolver {

        @Override
        public boolean supportsParameter(
                ParameterContext parameterContext, @NonNull ExtensionContext extensionContext) {
            return parameterContext.getParameter().getType().equals(ExtensionContext.class);
        }

        @Override
        public Object resolveParameter(
                @NonNull ParameterContext parameterContext, @NonNull ExtensionContext extensionContext) {
            return extensionContext;
        }
    }
}
