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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import org.apache.commons.io.FileUtils;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.ProcessingInstruction;
import org.dom4j.io.SAXReader;
import org.jvnet.hudson.test.junit.GroupedTest;
import org.kohsuke.stapler.MetaClassLoader;
import org.kohsuke.stapler.jelly.JellyClassLoaderTearOff;

/**
 * Builds up a {@link TestSuite} for performing static syntax checks on Jelly scripts.
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

    /**
     * Given a jar file or a class file directory, recursively search all the Jelly files and build a {@link TestSuite}
     * that performs static syntax checks.
     */
    public static TestSuite build(File res, boolean requirePI, boolean requireNoInlineJS) throws Exception {
        TestSuite ts = new JellyTestSuite();
        final JellyClassLoaderTearOff jct = new MetaClassLoader(JellyTestSuiteBuilder.class.getClassLoader())
                .loadTearOff(JellyClassLoaderTearOff.class);
        for (Map.Entry<URL, String> entry : scan(res, "jelly").entrySet()) {
            ts.addTest(new JellyCheck(entry.getKey(), entry.getValue(), jct, requirePI, requireNoInlineJS));
        }
        return ts;
    }

    private static class JellyCheck extends TestCase {

        private static final Logger LOGGER = Logger.getLogger(JellyCheck.class.getName());

        private final URL jelly;
        private final JellyClassLoaderTearOff jct;
        private final boolean requirePI;
        private List<String> errors = new ArrayList<>();
        private boolean inlineJs = false;
        private final boolean requireNoInlineJS;

        JellyCheck(URL jelly, String name, JellyClassLoaderTearOff jct, boolean requirePI, boolean requireNoInlineJS) {
            super(name);
            this.jelly = jelly;
            this.jct = jct;
            this.requirePI = requirePI;
            this.requireNoInlineJS = requireNoInlineJS;
        }

        @Override
        protected void runTest() throws Exception {
            jct.createContext().compileScript(jelly);
            Document dom = new SAXReader().read(jelly);
            if (requirePI) {
                ProcessingInstruction pi = dom.processingInstruction("jelly");
                if (pi == null || !pi.getText().contains("escape-by-default")) {
                    errors.add("<?jelly escape-by-default='true'?> is missing in " + jelly);
                }
            }
            // TODO: what else can we check statically? use of taglibs?
            checkScriptElement(dom);
            checkJavaScriptAttributes(dom);
            if (!errors.isEmpty()) {
                if (inlineJs) {
                    errors.add("Please visit https://www.jenkins.io/doc/developer/security/csp/ for more details.");
                }
                String message = String.join("\n", errors);
                throw new AssertionError(message);
            }
        }

        private void checkJavaScriptAttributes(Document dom) {
            List<Node> allNodes = dom.selectNodes("//*");
            allNodes.forEach(n -> {
                Element element = (Element) n;
                Attribute onclick = element.attribute("onclick");
                Attribute checkUrl = element.attribute("checkUrl");
                Attribute checkDependsOn = element.attribute("checkDependsOn");
                if (checkUrl != null && checkDependsOn == null) {
                    reportInlineJSUsage("Usage of 'checkUrl' without 'checkDependsOn' in " + jelly);
                }
                if (onclick != null && element.getNamespace() != Namespace.NO_NAMESPACE) {
                    reportInlineJSUsage("Usage of 'onclick' from a taglib in " + jelly);
                }
                List<Attribute> attributes = element.attributes();
                if (element.getNamespace() == Namespace.NO_NAMESPACE && !attributes.isEmpty()) {
                    attributes.forEach(a -> {
                        if (a.getName().startsWith("on")) {
                            reportInlineJSUsage("Usage of inline event handler '" + a.getName() + "' in " + jelly);
                        }
                    });
                }
            });
        }

        private void checkScriptElement(Document dom) {
            List<Node> scriptTags = dom.selectNodes("//script");
            scriptTags.forEach(n -> {
                Element element = (Element) n;
                String typeAttribute = element.attributeValue("type");
                if (element.attributeValue("src") == null
                        && (typeAttribute == null
                                || !"application/json".equals(typeAttribute.toLowerCase(Locale.US)))) {
                    reportInlineJSUsage("inline <script> element in " + jelly);
                }
            });
        }

        private void reportInlineJSUsage(String message) {
            if (requireNoInlineJS) {
                errors.add(message);
                inlineJs = true;
            } else {
                LOGGER.warning(message);
            }
        }

        private boolean isConfigJelly() {
            return jelly.toString().endsWith("/config.jelly");
        }

        private boolean isGlobalJelly() {
            return jelly.toString().endsWith("/global.jelly");
        }
    }

    /**
     * Execute all the Jelly tests in a servlet request handling context. To do so, we reuse HudsonTestCase
     */
    private static final class JellyTestSuite extends GroupedTest {
        HudsonTestCase h = new HudsonTestCase("Jelly test wrapper") {};

        @Override
        protected void setUp() throws Exception {
            h.setUp();
        }

        @Override
        protected void tearDown() throws Exception {
            h.tearDown();
        }

        private void doTests(TestResult result) throws Exception {
            super.runGroupedTests(result);
        }

        @Override
        protected void runGroupedTests(final TestResult result) throws Exception {
            h.executeOnServer(new Callable<>() {
                // this code now inside a request handling thread
                @Override
                public Object call() throws Exception {
                    doTests(result);
                    return null;
                }
            });
        }
    }
}
