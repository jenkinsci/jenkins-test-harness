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
package org.jvnet.hudson.test.junit.jupiter.timeout;

import hudson.model.listeners.ItemListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

@WithJenkins
class JenkinsRuleTimeout6Test extends JenkinsRuleTimeoutTestBase {

    // this is the only way to make JenkinsRule#timeout work here
    private static final String TIMEOUT = System.setProperty("jenkins.test.timeout", "30");

    @AfterEach
    void tearDown() {
        if (TIMEOUT != null) {
            System.setProperty("jenkins.test.timeout", TIMEOUT);
        } else {
            System.clearProperty("jenkins.test.timeout");
        }
    }

    @Test
    void hangUninterruptiblyInStartup() {
        fail("should not get here");
    }

    @TestExtension
    public static class HangsUninterruptibly extends ItemListener {
        @Override
        public void onLoaded() {
            assertDoesNotThrow(JenkinsRuleTimeoutTestBase::hangUninterruptibly);
        }
    }

}
