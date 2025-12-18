package org.jvnet.hudson.test.injected;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.PluginWrapper;
import hudson.cli.CLICommand;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.dom4j.Document;
import org.dom4j.ProcessingInstruction;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.MetaClassLoader;
import org.kohsuke.stapler.jelly.JellyClassLoaderTearOff;

/**
 * Base class for tests injected via the <code>maven-hpi-plugin</code>.
 * All injected tests should be put into this class in order to be executed by the <code>maven-hpi-plugin</code>.
 */
@WithJenkins
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class InjectedTestBase {

    private static final JellyClassLoaderTearOff JCT =
            new MetaClassLoader(InjectedTestBase.class.getClassLoader()).loadTearOff(JellyClassLoaderTearOff.class);

    private static JenkinsRule jenkinsRule;

    private final String artifactId;
    private final File outputDirectory;
    private final boolean requirePi;

    /**
     * Default constructor for initialization.
     * @param groupId a plugin groupId
     * @param artifactId a plugin artifactId
     * @param version a plugin version
     * @param outputDirectory a build output directory
     * @param requirePi if {@link ProcessingInstruction} are required
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    protected InjectedTestBase(
            String groupId, String artifactId, String version, String outputDirectory, boolean requirePi) {
        Objects.requireNonNull(groupId, "Missing configuration value for 'InjectedTestBase.groupId'");
        this.artifactId =
                Objects.requireNonNull(artifactId, "Missing configuration value for 'InjectedTestBase.artifactId'");
        Objects.requireNonNull(version, "Missing configuration value for 'InjectedTestBase.version'");
        this.outputDirectory = new File(Objects.requireNonNull(
                outputDirectory, "Missing configuration value for 'InjectedTestBase.outputDirectory'"));
        this.requirePi = requirePi;

        System.out.println("Running InjectedTest for " + groupId + ":" + artifactId + ":" + version);
    }

    /**
     * Common initializer for injected tests.
     * Parses the configuration parameters from the given {@link ExtensionContext}.
     *
     * @param rule a {@link JenkinsRule} to be used by tests
     */
    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    @Test
    @WithoutJenkins
    void testCliSanity() {
        CLICommand.clone("help");
    }

    @Test
    void testPluginActive() {
        if (!Jenkins.get().getPluginManager().getFailedPlugins().isEmpty()) {
            fail("While testing " + artifactId + " the following plugins failed to start: \n"
                    + String.join(
                            "\n",
                            Jenkins.get().getPluginManager().getFailedPlugins().stream()
                                    .map(fp -> "\t" + fp.name + " - " + fp.cause)
                                    .toList()));
        }

        PluginWrapper pw = Jenkins.get().getPluginManager().getPlugin(artifactId);

        assertNotNull(pw, artifactId + " failed to start");
        assertTrue(pw.isActive(), artifactId + " was not active");
    }

    Stream<Arguments> jellyResources() throws Exception {
        Map<String, URL> resources = scan("jelly");
        if (resources.isEmpty()) {
            return Stream.of(Arguments.of(Named.of("empty", new URI("file:///empty.jelly").toURL())));
        } else {
            return resources.entrySet().stream().map(e -> Arguments.of(Named.of(e.getKey(), e.getValue())));
        }
    }

    @ParameterizedTest
    @MethodSource("jellyResources")
    void testParseJelly(URL resource) throws Exception {
        assumeFalse(resource.toURI().equals(new URI("file:///empty.jelly")), "No jelly file found - skipping test");

        jenkinsRule.executeOnServer(() -> {
            JCT.createContext().compileScript(resource);
            Document dom = new SAXReader().read(resource);
            if (requirePi) {
                ProcessingInstruction pi = dom.processingInstruction("jelly");
                if (pi == null || !pi.getText().contains("escape-by-default")) {
                    fail("<?jelly escape-by-default='true'?> is missing in " + resource);
                }
            }
            return null;
        });
        // TODO: what else can we check statically? use of taglibs?
    }

    Stream<Arguments> propertiesResources() throws Exception {
        Map<String, URL> resources = scan("properties");
        if (resources.isEmpty()) {
            return Stream.of(Arguments.of(Named.of("empty", new URI("file:///empty.properties").toURL())));
        } else {
            return resources.entrySet().stream().map(e -> Arguments.of(Named.of(e.getKey(), e.getValue())));
        }
    }

    @ParameterizedTest
    @MethodSource("propertiesResources")
    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD")
    @WithoutJenkins
    void testProperties(URL resource) throws Exception {
        assumeFalse(
                resource.toURI().equals(new URI("file:///empty.properties")),
                "No properties file found - skipping test");

        Properties props = new Properties() {
            @Override
            public synchronized Object put(Object key, Object value) {
                Object old = super.put(key, value);
                assertNull(old, "Two values for `" + key + "` (`" + old + "` vs. `" + value + "`) in " + resource);
                return null;
            }
        };

        try (InputStream is = resource.openStream()) {
            byte[] contents = is.readAllBytes();
            if (!isEncoded(contents, StandardCharsets.US_ASCII)) {
                boolean isUtf8 = isEncoded(contents, StandardCharsets.UTF_8);
                boolean isIso88591 = isEncoded(contents, StandardCharsets.ISO_8859_1);
                assertTrue(!isUtf8 && !isIso88591, resource + " must be either valid UTF-8 or valid ISO-8859-1.");
            }
        }

        try (InputStream is = resource.openStream()) {
            PropertyResourceBundle propertyResourceBundle = new PropertyResourceBundle(is);
            propertyResourceBundle
                    .getKeys()
                    .asIterator()
                    .forEachRemaining(key -> props.setProperty(key, propertyResourceBundle.getString(key)));
        }
    }

    /**
     * Scans the {@link #outputDirectory} for files with the given extension.
     *
     * @param extension the file extension
     * @return a map containing the {@link URL} to a resource as value and the string representation as key
     * @throws IOException for errors when scanning the {@link #outputDirectory}
     */
    private Map<String, URL> scan(String extension) throws IOException {
        Map<String, URL> result = new HashMap<>();
        if (outputDirectory.isDirectory()) {
            for (File f : FileUtils.listFiles(outputDirectory, new String[] {extension}, true)) {
                result.put(
                        f.getAbsolutePath()
                                .substring((outputDirectory.getAbsolutePath() + java.io.File.separator).length()),
                        f.toURI().toURL());
            }
        } else if (outputDirectory.getName().endsWith(".jar")) {
            String jarUrl = outputDirectory.toURI().toURL().toExternalForm();
            try (JarFile jf = new JarFile(outputDirectory)) {
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
     * Check if the given bytes are encoded with the given charset.
     * @param bytes the bytes to check
     * @param charset the charset to use
     * @return if the bytes are encoded properly
     */
    private static boolean isEncoded(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        try {
            decoder.decode(buffer);
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
