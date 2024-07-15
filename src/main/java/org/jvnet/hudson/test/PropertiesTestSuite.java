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
import java.util.Map;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Checks things about {@code *.properties}.
 */
public class PropertiesTestSuite extends TestSuite {

    public PropertiesTestSuite(File resources) throws IOException {
        for (Map.Entry<URL,String> entry : JellyTestSuiteBuilder.scan(resources, "properties").entrySet()) {
            addTest(new PropertiesTest(entry.getKey(), entry.getValue()));
        }
    }

    private static class PropertiesTest extends TestCase {

        private final URL resource;

        private PropertiesTest(URL resource, String name) {
            super(name);
            this.resource = resource;
        }

        @Override
        protected void runTest() throws Throwable {
            Properties props = new Properties() {
                @Override
                public synchronized Object put(Object key, Object value) {
                    Object old = super.put(key, value);
                    if (old != null) {
                        throw new AssertionError("Two values for `" + key + "` (`" + old + "` vs. `" + value + "`) in " + resource);
                    }
                    return null;
                }
            };

            try (InputStream is = resource.openStream()) {
                byte[] contents = is.readAllBytes();
                if (!isEncoded(contents, StandardCharsets.US_ASCII)) {
                    boolean isUtf8 = isEncoded(contents, StandardCharsets.UTF_8);
                    boolean isIso88591 = isEncoded(contents, StandardCharsets.ISO_8859_1);
                    if (!isUtf8 && !isIso88591) {
                        throw new AssertionError(resource + " must be either valid UTF-8 or valid ISO-8859-1.");
                    }
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
