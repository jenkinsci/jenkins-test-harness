/*
 * The MIT License
 *
 * Copyright 2025 Jenkins project contributors
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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * Test basic behavior of {@link JenkinsSessionRule}
 */
public class JenkinsSessionRuleTest {

    @Rule
    public final JenkinsSessionRule rule = new JenkinsSessionRule();

    @Before
    public void before() {
        assertNotNull(rule.getHome());
        assertTrue(rule.getHome().exists());
    }

    @After
    public void after() {
        assertTrue(rule.getHome().exists());
    }

    @Test
    public void testRestart() throws Throwable {
        assertNotNull(rule.getHome());
        assertTrue(rule.getHome().exists());

        File[] homes = new File[2];
        URL[] urls = new URL[2];

        rule.then(r -> {
            homes[0] = r.jenkins.getRootDir();
            urls[0] = r.getURL();
        });

        rule.then(r -> {
            homes[1] = r.jenkins.getRootDir();
            urls[1] = r.getURL();
        });

        assertEquals(homes[0], homes[1]);
        assertEquals(urls[0], urls[1]);
    }
}
