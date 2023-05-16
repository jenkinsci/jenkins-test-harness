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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.SidACL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.acegisecurity.acls.sid.Sid;

/**
 * An authorization strategy configured in a fluent style from test code.
 * Install using {@link Jenkins#setAuthorizationStrategy}.
 * You probably also want to call {@link Jenkins#setSecurityRealm} on {@link JenkinsRule#createDummySecurityRealm}.
 */
public class MockAuthorizationStrategy extends AuthorizationStrategy {
    
    private final List<Grant.GrantOn.GrantOnTo> grantsOnTo = new ArrayList<>();

    /** Creates a new strategy granting no permissions. */
    public MockAuthorizationStrategy() {}

    /**
     * Begin granting a set of permissions.
     * Note that grants cannot be subsequently revoked, but you could reset the strategy to a newly configured one.
     * @param permissions which permissions to grant ({@link Permission#impliedBy} is honored)
     */
    public Grant grant(Permission... permissions) {
        Set<Permission> effective = new HashSet<>(List.of(permissions));
        boolean added = true;
        while (added) {
            added = false;
            for (Permission p : Permission.getAll()) {
                added |= effective.contains(p.impliedBy) && effective.add(p);
            }
        }
        return new Grant(effective);
    }

    /**
     * Like {@link #grant} but does <em>not</em> honor {@link Permission#impliedBy}.
     */
    public Grant grantWithoutImplication(Permission... permissions) {
        return new Grant(new HashSet<>(List.of(permissions)));
    }

    /**
     * A grant of a set of permissions.
     * You must proceed to specify where they should be granted.
     */
    public class Grant {

        private final Set<String> permissions;

        Grant(Set<Permission> permissions) {
            this.permissions = permissions.stream().map(Permission::getId).collect(Collectors.toSet());
        }

        /**
         * Everywhere in Jenkins.
         */
        public GrantOn everywhere() {
            return onPaths(".*");
        }

        /**
         * On {@code Jenkins} itself, but not any child objects.
         */
        public GrantOn onRoot() {
            return onPaths("");
        }

        /**
         * On some items such as jobs.
         * If some of these happen to be {@link ItemGroup}s, the grant is <em>not</em> applied to children.
         */
        public GrantOn onItems(Item... items) {
            String[] paths = new String[items.length];
            for (int i = 0; i < items.length; i++) {
                paths[i] = Pattern.quote(items[i].getFullName());
            }
            return onPaths(paths);
        }

        /**
         * On some item groups, typically folders.
         * The grant applies to the folder itself as well as any (direct or indirect) children.
         */
        public GrantOn onFolders(ItemGroup<?>... folders) {
            String[] paths = new String[folders.length];
            for (int i = 0; i < folders.length; i++) {
                paths[i] = Pattern.quote(folders[i].getFullName()) + "(|/.+)";
            }
            return onPaths(paths);
        }

        /**
         * On some item path expressions.
         * Each element is an implicitly rooted regular expression.
         * {@code Jenkins} itself is {@code ""}, a top-level job would be {@code "jobname"}, a nested job would be {@code "folder/jobname"}, etc.
         * Grants are <em>not</em> implicitly applied to child objects.
         */
        public GrantOn onPaths(String... pathRegexps) {
            StringBuilder b = new StringBuilder();
            boolean first = true;
            for (String rx : pathRegexps) {
                if (first) {
                    first = false;
                } else {
                    b.append('|');
                }
                b.append("(?:").append(rx).append(')');
            }
            return new GrantOn(b.toString());
        }

        /**
         * A grant of some permissions in certain places.
         * You must proceed to specify to whom the grant is made.
         */
        public class GrantOn {

            private final Pattern regexp;

            GrantOn(String regexp) {
                this.regexp = Pattern.compile(regexp);
            }

            /** To some users or groups. */
            public MockAuthorizationStrategy to(String... sids) {
                return new GrantOnTo(new HashSet<>(List.of(sids))).add();
            }

            /** To some users. */
            public MockAuthorizationStrategy to(User... users) {
                String[] sids = new String[users.length];
                for (int i = 0; i < users.length; i++) {
                    sids[i] = users[i].getId();
                }
                return to(sids);
            }

            /** To everyone, including anonymous users. */
            public MockAuthorizationStrategy toEveryone() {
                return to(/* SidACL.toString(ACL.EVERYONE) */"role_everyone");
            }

            /** To all authenticated users. */
            public MockAuthorizationStrategy toAuthenticated() {
                return to(/* SecurityRealm.AUTHENTICATED_AUTHORITY */"authenticated");
            }

            private class GrantOnTo {

                private final Set<String> sids;

                GrantOnTo(Set<String> sids) {
                    this.sids = sids;
                }

                MockAuthorizationStrategy add() {
                    grantsOnTo.add(this);
                    return MockAuthorizationStrategy.this;
                }

                boolean matches(String path, String name, Permission permission) {
                    return regexp.matcher(path).matches() &&
                        sids.contains(name) && // TODO consider IdStrategy
                        permissions.contains(permission.getId());
                }

            }

        }

    }

    @NonNull
    @Override
    public ACL getRootACL() {
        return new ACLImpl("");
    }

    @NonNull
    @Override
    public ACL getACL(AbstractItem item) {
        return new ACLImpl(item.getFullName());
    }

    @NonNull
    @Override
    public ACL getACL(@NonNull Job<?, ?> project) {
        return getACL((AbstractItem) project); // stupid overload
    }

    private class ACLImpl extends SidACL {

        private final String path;

        ACLImpl(String path) {
            this.path = path;
        }

        @Override protected Boolean hasPermission(Sid p, Permission permission) {
            String name = toString(p);
            for (Grant.GrantOn.GrantOnTo grantOnTo : grantsOnTo) {
                if (grantOnTo.matches(path, name, permission)) {
                    return true;
                }
            }
            return null; // allow groups to be checked after users, etc.
        }

    }

    @NonNull
    @Override
    public Collection<String> getGroups() {
        return Collections.emptySet(); // we do not differentiate usernames from groups
    }
}

