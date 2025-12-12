package org.jvnet.hudson.test.injected;

import static org.junit.jupiter.api.Assertions.fail;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.net.URL;
import java.util.stream.Stream;
import org.dom4j.Document;
import org.dom4j.ProcessingInstruction;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.MetaClassLoader;
import org.kohsuke.stapler.jelly.JellyClassLoaderTearOff;

/**
 * Jelly tests injected via the <code>maven-hpi-plugin</code>.
 */
@WithJenkins
public class JellyTest extends InjectedTest {

    private final JellyClassLoaderTearOff jct =
            new MetaClassLoader(JellyTest.class.getClassLoader()).loadTearOff(JellyClassLoaderTearOff.class);

    @SuppressWarnings("unused")
    private JenkinsRule jenkinsRule;

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN")
    static Stream<Arguments> resources() throws Exception {
        return scan(new File(outputDirectory), "jelly").entrySet().stream()
                .map(e -> Arguments.of(Named.of(e.getKey(), e.getValue())));
    }

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    @ParameterizedTest
    @MethodSource("resources")
    void testParseJelly(URL resource) throws Exception {
        jct.createContext().compileScript(resource);
        Document dom = new SAXReader().read(resource);
        if (requirePI) {
            ProcessingInstruction pi = dom.processingInstruction("jelly");
            if (pi == null || !pi.getText().contains("escape-by-default")) {
                fail("<?jelly escape-by-default='true'?> is missing in " + resource);
            }
        }
        // TODO: what else can we check statically? use of taglibs?
    }
}
