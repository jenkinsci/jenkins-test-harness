package org.jvnet.hudson.test;

import hudson.security.Permission;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Validates that JUnit4 and JUnit5 based {@link JenkinsRule} implementations have the same default authorization and permissions.
 */
@SuppressWarnings("deprecation")
public class JenkinsRulePermissionTest {

    @Rule
    public JenkinsRule junit4rule = new JenkinsRule();

    @org.junit.Test
    public void junit4() {
        MockAuthorizationStrategy strategy = new MockAuthorizationStrategy();
        junit4rule.getInstance().setAuthorizationStrategy(strategy);

        // basic permissions
        Assert.assertTrue(junit4rule.getInstance().getACL().hasPermission(Permission.READ));
        Assert.assertTrue(junit4rule.getInstance().getACL().hasPermission(Permission.WRITE));
        Assert.assertTrue(junit4rule.getInstance().getACL().hasPermission(Permission.CREATE));
        Assert.assertTrue(junit4rule.getInstance().getACL().hasPermission(Permission.UPDATE));
        Assert.assertTrue(junit4rule.getInstance().getACL().hasPermission(Permission.DELETE));
        Assert.assertTrue(junit4rule.getInstance().getACL().hasPermission(Permission.CONFIGURE));

        // admin permissions
        Assert.assertTrue(junit4rule.getInstance().getACL().hasPermission(Permission.FULL_CONTROL));
        Assert.assertTrue(junit4rule.getInstance().getACL().hasPermission(Permission.HUDSON_ADMINISTER));
    }

    @org.junit.jupiter.api.Test
    @WithJenkins
    void junit5(JenkinsRule junit5rule) {
        MockAuthorizationStrategy strategy = new MockAuthorizationStrategy();
        junit5rule.getInstance().setAuthorizationStrategy(strategy);

        // basic permissions
        Assertions.assertTrue(junit5rule.getInstance().getACL().hasPermission(Permission.READ));
        Assertions.assertTrue(junit5rule.getInstance().getACL().hasPermission(Permission.WRITE));
        Assertions.assertTrue(junit5rule.getInstance().getACL().hasPermission(Permission.CREATE));
        Assertions.assertTrue(junit5rule.getInstance().getACL().hasPermission(Permission.UPDATE));
        Assertions.assertTrue(junit5rule.getInstance().getACL().hasPermission(Permission.DELETE));
        Assertions.assertTrue(junit5rule.getInstance().getACL().hasPermission(Permission.CONFIGURE));

        // admin permissions
        Assertions.assertTrue(junit5rule.getInstance().getACL().hasPermission(Permission.FULL_CONTROL));
        Assertions.assertTrue(junit5rule.getInstance().getACL().hasPermission(Permission.HUDSON_ADMINISTER));
    }
}
