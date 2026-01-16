/*
 * The MIT License
 *
 * Copyright 2021 CloudBees, Inc.
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

package org.jvnet.hudson.test.fixtures;

import hudson.Plugin;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.PluginUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Plugin for use <b>internally only</b> by {@link RealJenkinsFixture}, do not use this from plugin test code!
 * <p>
 * <strong>NOTE</strong>: this and only this class is added into a dynamically generated plugin, see {@link PluginUtils#createRealJenkinsFixturePlugin(File, String)}.
 * In order for this to occur correctly there need to be no inner classes or other code dependencies here (except what can be loaded by reflection).
 */
@Restricted(NoExternalUse.class)
public class RealJenkinsFixtureInit extends Plugin {

    @SuppressWarnings("deprecation")
    public RealJenkinsFixtureInit() {
        // @Initializer just gets run too late, even with before = InitMilestone.PLUGINS_PREPARED
    }

    @Override
    public void start() throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(
                "RealJenkinsFixtureInit",
                new URL[] {new URL(System.getProperty("RealJenkinsFixture.location"))},
                ClassLoader.getSystemClassLoader().getParent())) {
            classLoader
                    .loadClass("org.jvnet.hudson.test.fixtures.RealJenkinsFixture$Init")
                    .getMethod("run", Object.class)
                    .invoke(null, Jenkins.get());
        }
    }
}
