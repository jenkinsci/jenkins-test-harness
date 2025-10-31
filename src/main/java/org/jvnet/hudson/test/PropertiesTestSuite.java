/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jvnet.hudson.test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;

/**
 * Checks things about {@code *.properties}.
 */
public class PropertiesTestSuite {

    public static DynamicContainer build(File resources) throws IOException {
        List<DynamicTest> tests = new ArrayList<>();

        for (Map.Entry<URL, String> entry :
                JellyTestSuiteBuilder.scan(resources, "properties").entrySet()) {
            tests.add(DynamicTest.dynamicTest(
                    "Check " + entry.getKey(), () -> new PropertiesTest(entry.getKey()).test()));
        }

        return DynamicContainer.dynamicContainer("Properties Tests", tests);
    }

    private static class PropertiesTest {

        private final URL resource;

        private PropertiesTest(URL resource) {
            this.resource = resource;
        }

        void test() throws Exception {
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
}
