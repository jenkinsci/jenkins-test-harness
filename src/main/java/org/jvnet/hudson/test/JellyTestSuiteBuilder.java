/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.commons.io.FileUtils;
import org.dom4j.Document;
import org.dom4j.ProcessingInstruction;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.kohsuke.stapler.MetaClassLoader;
import org.kohsuke.stapler.jelly.JellyClassLoaderTearOff;

/**
 * Builds up a {@link DynamicContainer} for performing static syntax checks on Jelly scripts.
 *
 * @author Kohsuke Kawaguchi
 */
public class JellyTestSuiteBuilder {

    static Map<URL, String> scan(File resources, String extension) throws IOException {
        Map<URL, String> result = new HashMap<>();
        if (resources.isDirectory()) {
            for (File f : FileUtils.listFiles(resources, new String[] {extension}, true)) {
                result.put(
                        f.toURI().toURL(),
                        f.getAbsolutePath().substring((resources.getAbsolutePath() + File.separator).length()));
            }
        } else if (resources.getName().endsWith(".jar")) {
            String jarUrl = resources.toURI().toURL().toExternalForm();
            JarFile jf = new JarFile(resources);
            Enumeration<JarEntry> e = jf.entries();
            while (e.hasMoreElements()) {
                JarEntry ent = e.nextElement();
                if (ent.getName().endsWith("." + extension)) {
                    result.put(new URL("jar:" + jarUrl + "!/" + ent.getName()), ent.getName());
                }
            }
            jf.close();
        }
        return result;
    }

    public static DynamicContainer build(File res, boolean requirePI) throws Exception {
        List<DynamicTest> tests = new ArrayList<>();

        final JellyClassLoaderTearOff jct = new MetaClassLoader(JellyTestSuiteBuilder.class.getClassLoader())
                .loadTearOff(JellyClassLoaderTearOff.class);
        for (Map.Entry<URL, String> entry : scan(res, "jelly").entrySet()) {
            tests.add(DynamicTest.dynamicTest(
                    entry.getValue(), () -> new JellyCheck(entry.getKey(), jct, requirePI).test()));
        }

        return DynamicContainer.dynamicContainer("Jelly Tests", tests);
    }

    private static class JellyCheck {
        private final URL jelly;
        private final JellyClassLoaderTearOff jct;
        private final boolean requirePI;

        private final HudsonTestCase h = new HudsonTestCase("JellyCheck#test") {};

        JellyCheck(URL jelly, JellyClassLoaderTearOff jct, boolean requirePI) {
            this.jelly = jelly;
            this.jct = jct;
            this.requirePI = requirePI;
        }

        void test() throws Throwable {
            h.setUp();

            jct.createContext().compileScript(jelly);
            Document dom = new SAXReader().read(jelly);
            if (requirePI) {
                ProcessingInstruction pi = dom.processingInstruction("jelly");
                if (pi == null || !pi.getText().contains("escape-by-default")) {
                    fail("<?jelly escape-by-default='true'?> is missing in " + jelly);
                }
            }
            // TODO: what else can we check statically? use of taglibs?

            h.tearDown();
        }
    }
}
