/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package jenkins.security;

import hudson.ExtensionList;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.userdetails.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import static org.junit.Assert.fail;

public class SpySecurityListenerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private SpySecurityListener spySecurityListener;

    @Before
    public void linkExtension() throws Exception {
        spySecurityListener = ExtensionList.lookup(SecurityListener.class).get(SpySecurityListenerImpl.class);
    }

    @Test
    public void testEventAreRegistered() {
        initiallyEmpty();

        checkAllEventFiredCorrectly();

        checkClearWork();
    }

    private void initiallyEmpty() {
        spySecurityListener.authenticatedCalls.assertNoNewEvents();
        spySecurityListener.failedToAuthenticateCalls.assertNoNewEvents();
        spySecurityListener.loggedInCalls.assertNoNewEvents();
        spySecurityListener.failedToLogInCalls.assertNoNewEvents();
        spySecurityListener.loggedOutCalls.assertNoNewEvents();
    }

    private void checkAllEventFiredCorrectly() {
        SecurityListener.fireAuthenticated(new User("test", "", true, true, true, true, new GrantedAuthority[0]));
        spySecurityListener.authenticatedCalls.assertLastEventIsAndThenRemoveIt(userDetails -> userDetails.getUsername().equals("test"));
        spySecurityListener.assertAllEmpty();

        SecurityListener.fireFailedToLogIn("test2");
        SecurityListener.fireLoggedIn("test3");

        spySecurityListener.failedToLogInCalls.assertLastEventIsAndThenRemoveIt("test2");
        spySecurityListener.loggedInCalls.assertLastEventIsAndThenRemoveIt("test3");
        spySecurityListener.assertAllEmpty();

        SecurityListener.fireFailedToAuthenticate("test4");
        SecurityListener.fireLoggedOut("test5");

        spySecurityListener.failedToAuthenticateCalls.assertLastEventIsAndThenRemoveIt("test4");
        spySecurityListener.loggedOutCalls.assertLastEventIsAndThenRemoveIt("test5");
        spySecurityListener.assertAllEmpty();
    }

    private void checkClearWork() {
        SecurityListener.fireFailedToAuthenticate("test6");
        SecurityListener.fireLoggedOut("test7");

        try {
            spySecurityListener.assertAllEmpty();
            fail();
        } catch (AssertionError e) {
        }

        spySecurityListener.clearPreviousCalls();
        spySecurityListener.assertAllEmpty();
    }

    @TestExtension
    public static class SpySecurityListenerImpl extends SpySecurityListener {}
}
