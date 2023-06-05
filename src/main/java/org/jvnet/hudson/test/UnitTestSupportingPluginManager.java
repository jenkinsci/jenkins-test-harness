/*
 * The MIT License
 *
 * Copyright (c) 2010, Kohsuke Kawaguchi
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

import hudson.LocalPluginManager;
import hudson.Plugin;
import hudson.PluginManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;

/**
 * {@link PluginManager} that can work with unit tests where dependencies are just jars.
 * {@link PluginManager} to speed up unit tests.
 * 
 *
 * <p>
 * Instead of loading every plugin for every test case, this allows them to reuse a single plugin manager.
 *
 * <p>
 * TODO: {@link Plugin} start/stop/postInitialize invocation semantics gets different. Perhaps
 * 
 * @author Kohsuke Kawaguchi
 * @see HudsonTestCase#useLocalPluginManager
 */
public class UnitTestSupportingPluginManager extends PluginManager {

    public UnitTestSupportingPluginManager(File rootDir) {
        super(null, new File(rootDir, "plugins"));
    }

    /** @see LocalPluginManager#loadBundledPlugins */
    @Override
    protected Collection<String> loadBundledPlugins() throws Exception {
        try {
            return loadBundledPlugins(new File(WarExploder.getExplodedDir(), "WEB-INF/plugins"));
        } finally {
            try {
                Method loadDetachedPlugins = PluginManager.class.getDeclaredMethod("loadDetachedPlugins");
                loadDetachedPlugins.setAccessible(true);
                loadDetachedPlugins.invoke(this);
            } catch (NoSuchMethodException x) {
                // Jenkins 1.x, fine
            }
        }
    }

    private Set<String> loadBundledPlugins(File fromDir) throws IOException, URISyntaxException {
        Set<String> names = new HashSet<>();

        File[] children = fromDir.listFiles();
        if (children!=null) {
            for (File child : children) {
                try {
                    names.add(child.getName());

                    copyBundledPlugin(child.toURI().toURL(), child.getName());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to extract the bundled plugin "+child,e);
                }
            }
        } else {
            LOGGER.log(Level.FINE, "No plugins loaded from {0}. Directory does not exist.", fromDir);
        }
        // If running tests for a plugin, include the plugin being tested
        URL u = getClass().getClassLoader().getResource("the.jpl");
        if(u==null){
        	u = getClass().getClassLoader().getResource("the.hpl"); // keep backward compatible 
        }
        if (u!=null) try {
            String thisPlugin;
            try (InputStream is = u.openStream()) {
                thisPlugin = new Manifest(is).getMainAttributes().getValue("Short-Name");
            }
            if (thisPlugin == null) {
                throw new IOException("malformed " + u);
            }
            names.add(thisPlugin + ".jpl");
            copyBundledPlugin(u, thisPlugin + ".jpl");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to copy the.jpl",e);
        }

        // and pick up test dependency *.jpi that are placed by maven-hpi-plugin TestDependencyMojo.
        // and copy them into $JENKINS_HOME/plugins.
        URL index = getClass().getResource("/test-dependencies/index");
        if (index!=null) {// if built with maven-hpi-plugin < 1.52 this file won't exist.
            try (BufferedReader r = new BufferedReader(new InputStreamReader(index.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line=r.readLine())!=null) {
                	final URL url = new URL(index, line + ".jpi");
					File f;
                    try {
                        f = new File(url.toURI());
                    } catch (IllegalArgumentException x) {
                        if (x.getMessage().equals("URI is not hierarchical")) {
                            throw new IOException(
                                    "You are probably trying to load plugins from within a jarfile (not possible). If"
                                            + " you are running this in your IDE and see this message, it is likely"
                                            + " that you have a clean target directory. Try running 'mvn test-compile' "
                                            + "from the command line (once only), which will copy the required plugins "
                                            + "into target/test-classes/test-dependencies - then retry your test", x);
                        } else {
                            throw new IOException(index + " contains bogus line " + line, x);
                        }
                    }
                    // TODO should this be running names.add(line + ".jpi")? Affects PluginWrapper.isBundled & .*Dependents
                	if(f.exists()){
                		copyBundledPlugin(url, line + ".jpi");
                	}else{
                		copyBundledPlugin(new URL(index, line + ".hpi"), line + ".jpi"); // fallback to hpi
                	}
                }
            }
        }

        return names;
    }
    
    /**
     * Dynamically load a detached plugin that would not otherwise get loaded.
     * Will only work in Jenkins 2.x.
     * May be called at any time after Jenkins starts up (do not use from {@link #loadBundledPlugins()}.
     * You may need to first install any transitive dependencies.
     * @param shortName {@code cvs} for example
     */
    public void installDetachedPlugin(String shortName) throws Exception {
        URL r = UnitTestSupportingPluginManager.class.getClassLoader().getResource("WEB-INF/detached-plugins/" + shortName + ".hpi");
        Assert.assertNotNull("could not find " + shortName, r);
        File f = new File(rootDir, shortName + ".hpi");
        FileUtils.copyURLToFile(r, f);
        dynamicLoad(f);
    }

    private static final Logger LOGGER = Logger.getLogger(UnitTestSupportingPluginManager.class.getName());

}
