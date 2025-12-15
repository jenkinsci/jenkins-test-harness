package org.jvnet.hudson.test.injected;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Properties tests injected via the <code>maven-hpi-plugin</code>.
 */
public class PropertiesTest extends InjectedTest {

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN")
    static Stream<Arguments> resources() throws Exception {
        return scan(new File(outputDirectory), "properties").entrySet().stream()
                .map(e -> Arguments.of(Named.of(e.getKey(), e.getValue())));
    }

    @ParameterizedTest
    @MethodSource("resources")
    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD")
    void testProperties(URL resource) throws Exception {
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
