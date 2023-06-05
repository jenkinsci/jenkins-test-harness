/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package org.jvnet.hudson.test.recipes;

import java.io.File;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URL;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Installs the specified plugins before launching Jenkins.
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
@Recipe(WithPlugin.RunnerImpl.class)
@JenkinsRecipe(WithPlugin.RuleRunnerImpl.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithPlugin {
    /**
     * Filenames of the plugins to install.
     * These are expected to be of the form {@code workflow-job.jpi}
     * where {@code /plugins/workflow-job.jpi} is a test classpath resource.
     * (The basename should be a plugin short name and the extension should be {@code jpi}.)
     * <p>Committing that file to SCM (say, {@code src/test/resources/plugins/sample.jpi})
     * is reasonable for small fake plugins built for this purpose and exercising some bit of code.
     * If you wish to test with larger archives of real plugins,
     * this is possible for example by binding {@code dependency:copy} to the {@code process-test-resources} phase.
     * <p>In most cases you do not need this annotation.
     * Simply add whatever plugins you are interested in testing against to your POM in {@code test} scope.
     * These, and their transitive dependencies, will be loaded in all {@link JenkinsRule} tests in this plugin.
     * This annotation is useful if only a particular test may load the tested plugin,
     * or if the tested plugin is not available in a repository for use as a test dependency.
     */
    String[] value();

    class RunnerImpl extends Recipe.Runner<WithPlugin> {
        private WithPlugin a;

        @Override
        public void setup(HudsonTestCase testCase, WithPlugin recipe) throws Exception {
            a = recipe;
            testCase.useLocalPluginManager = true;
        }

        @Override
        public void decorateHome(HudsonTestCase testCase, File home) throws Exception {
            for (String plugin : a.value()) {
                URL res = getClass().getClassLoader().getResource("plugins/" + plugin);
                FileUtils.copyURLToFile(res, new File(home, "plugins/" + plugin));
            }
        }
    }

    class RuleRunnerImpl extends JenkinsRecipe.Runner<WithPlugin> {
        private WithPlugin a;

        @Override
        public void setup(JenkinsRule jenkinsRule, WithPlugin recipe) throws Exception {
            a = recipe;
            jenkinsRule.useLocalPluginManager = true;
        }

        @Override
        public void decorateHome(JenkinsRule jenkinsRule, File home) throws Exception {
            for (String plugin : a.value()) {
                URL res = getClass().getClassLoader().getResource("plugins/" + plugin);
                FileUtils.copyURLToFile(res, new File(home, "plugins/" + plugin));
            }
        }
    }
}
