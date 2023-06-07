/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.main.UseRecipesWithJenkinsRuleTest;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Complements {@link UseRecipesWithJenkinsRuleTest} by running with class-scoped test resources.
 */
public class LocalDataTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @LocalData
    @Test
    public void works() {
        assertNotNull(r.jenkins.getItem("somejob"));
    }

    @LocalData
    @Test
    public void methodData() {
        assertEquals("This is Jenkins in LocalDataTest#methodData", r.jenkins.getSystemMessage());
    }

    @LocalData("methodData")
    @Test
    public void otherData() {
        assertEquals("This is Jenkins in LocalDataTest#methodData", r.jenkins.getSystemMessage());
    }

}
