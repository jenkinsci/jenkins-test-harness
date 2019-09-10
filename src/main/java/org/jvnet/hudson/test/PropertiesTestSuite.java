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
import java.util.Map;
import java.util.Properties;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import static org.jvnet.hudson.test.JellyTestSuiteBuilder.scan;

/**
 * Checks things about {@code *.properties}.
 */
public class PropertiesTestSuite extends TestSuite {

    public PropertiesTestSuite(File resources) throws IOException {
        for (Map.Entry<URL,String> entry : scan(resources, "properties").entrySet()) {
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
                props.load(is);
            }
        }

    }

}
