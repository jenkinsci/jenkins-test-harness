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

package org.jvnet.hudson.test;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;

public class MockAuthorizationStrategyTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    
    @Test
    public void smokes() throws Exception {
        final MockFolder d = r.createFolder("d");
        final FreeStyleProject p = d.createProject(FreeStyleProject.class, "p");
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to("root").
            grantWithoutImplication(Jenkins.ADMINISTER).onRoot().to("admin").
            grant(Jenkins.READ, Item.READ).everywhere().toEveryone().
            grant(Item.EXTENDED_READ).everywhere().toAuthenticated().
            grant(Item.CREATE).onItems(d).to("dev").
            grant(Item.CONFIGURE).onItems(p).to("dev").
            grant(Item.BUILD).onFolders(d).to("dev"));
        ACL.impersonate(User.get("root").impersonate(), new Runnable() {
            @Override
            public void run() {
                assertTrue(r.jenkins.hasPermission(Jenkins.RUN_SCRIPTS));
                assertTrue(p.hasPermission(Item.DELETE));
            }
        });
        ACL.impersonate(User.get("admin").impersonate(), new Runnable() {
            @Override
            public void run() {
                assertFalse(r.jenkins.hasPermission(Jenkins.RUN_SCRIPTS));
                assertTrue(r.jenkins.hasPermission(Jenkins.ADMINISTER));
                assertFalse(p.hasPermission(Item.DELETE));
                assertTrue(p.hasPermission(Item.READ));
            }
        });
        ACL.impersonate(User.get("dev").impersonate(), new Runnable() {
            @Override
            public void run() {
                assertFalse(r.jenkins.hasPermission(Jenkins.ADMINISTER));
                assertTrue(r.jenkins.hasPermission(Jenkins.READ));
                assertFalse(p.hasPermission(Item.DELETE));
                assertTrue(p.hasPermission(Item.CONFIGURE));
                assertTrue(p.hasPermission(Item.BUILD));
                assertFalse(d.hasPermission(Item.CONFIGURE));
                assertTrue(d.hasPermission(Item.CREATE));
                assertTrue(d.hasPermission(Item.READ));
                assertTrue(d.hasPermission(Item.EXTENDED_READ));
                assertFalse(p.hasPermission(Item.CREATE));
            }
        });
        ACL.impersonate(Jenkins.ANONYMOUS, new Runnable() {
            @Override
            public void run() {
                assertFalse(r.jenkins.hasPermission(Jenkins.ADMINISTER));
                assertTrue(r.jenkins.hasPermission(Jenkins.READ));
                assertFalse(p.hasPermission(Item.DELETE));
                assertTrue(p.hasPermission(Item.READ));
                assertFalse(p.hasPermission(Item.EXTENDED_READ));
            }
        });
        assertTrue("SYSTEM has everything", r.jenkins.hasPermission(Jenkins.RUN_SCRIPTS)); // handled by SidACL
    }

    @Test
    public void noPermissionsByDefault() {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        assertFalse(r.jenkins.getACL().hasPermission(User.get("alice").impersonate(), Jenkins.ADMINISTER));
    }

}
