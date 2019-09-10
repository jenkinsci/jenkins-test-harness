/*
 * The MIT License
 *
 * Copyright 2013 Red Hat, Inc.
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

package hudson.cli;

import hudson.Extension;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.SecurityRealm;
import hudson.security.SidACL;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import jenkins.model.Jenkins;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.Sid;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

/**
 * Helper class to invoke {@link CLICommand} and check the response.
 *
 * @author ogondza
 */
public class CLICommandInvoker {

    private static final String username = "user";
    private final JenkinsRule rule;
    private final CLICommand command;
    @Deprecated
    private SecurityRealm originalSecurityRealm = null;
    @Deprecated
    private AuthorizationStrategy originalAuthorizationStrategy = null;
    @Deprecated
    private SecurityContext originalSecurityContext = null;

    private InputStream stdin;
    private List<String> args = Collections.emptyList();
    @Deprecated
    private List<Permission> permissions = Collections.emptyList();
    private Locale locale = Locale.ENGLISH;

    public CLICommandInvoker(final JenkinsRule rule, final CLICommand command) {

        if (command.getClass().getAnnotation(Extension.class) == null) {

            throw new AssertionError(String.format(
                    "Command %s is missing @Extension annotation.",
                    command.getClass()
            ));
        }

        this.rule = rule;
        this.command = command;
    }

    public CLICommandInvoker(final JenkinsRule rule, final String command) {
        this.rule = rule;
        this.command = CLICommand.clone(command);

        if (this.command == null) throw new AssertionError("No such command: " + command);
    }

    /**
     * @deprecated Rather use {@link #asUser}.
     */
    @Deprecated
    public CLICommandInvoker authorizedTo(final Permission... permissions) {

        this.permissions = Arrays.asList(permissions);
        return this;
    }

    /**
     * Run the command as a given username.
     * Test setup should have first defined a meaningful security realm and authorization strategy.
     * @see Jenkins#setSecurityRealm
     * @see JenkinsRule#createDummySecurityRealm
     * @see Jenkins#setAuthorizationStrategy
     * @see MockAuthorizationStrategy
     */
    public CLICommandInvoker asUser(String user) {
        command.setTransportAuth(User.get(user).impersonate());
        return this;
    }

    public CLICommandInvoker withStdin(final InputStream stdin) {

        if (stdin == null) throw new NullPointerException("No stdin provided");

        this.stdin = stdin;
        return this;
    }

    public CLICommandInvoker withArgs(final String... args) {

        this.args = Arrays.asList(args);
        return this;
    }

    public Result invokeWithArgs(final String... args) {

        return withArgs(args).invoke();
    }

    public Result invoke() {

        setAuth();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();

        final int returnCode = command.main(
                args, locale, stdin, new PrintStream(out), new PrintStream(err)
        );

        restoreAuth();

        return new Result(returnCode, out, err);
    }

    private static class GrantPermissions extends AuthorizationStrategy {
        final String username;
        final List<Permission> permissions;
        GrantPermissions(String username, List<Permission> permissions) {
            this.username = username;
            this.permissions = permissions;
            for (Permission p : permissions) {
                p.setEnabled(true);
            }
        }
        @Override
        public ACL getRootACL() {
            return new SidACL() {
                @Override
                protected Boolean hasPermission(Sid u, Permission permission) {
                    if (u instanceof PrincipalSid && ((PrincipalSid) u).getPrincipal().equals(username)) {
                        for (Permission p = permission; p != null; p = p.impliedBy) {
                            if (permissions.contains(p)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
            };
        }
        @Override
        public Collection<String> getGroups() {
            return Collections.emptySet();
        }
    }

    private void setAuth() {

        if (permissions.isEmpty()) return;

        JenkinsRule.DummySecurityRealm realm = rule.createDummySecurityRealm();
        realm.addGroups(username, "group");

        originalSecurityRealm = rule.jenkins.getSecurityRealm();
        rule.jenkins.setSecurityRealm(realm);

        originalAuthorizationStrategy = rule.jenkins.getAuthorizationStrategy();
        rule.jenkins.setAuthorizationStrategy(new GrantPermissions(username, permissions));

        command.setTransportAuth(user().impersonate());
        // Otherwise it is SYSTEM, which would be relevant for a command overriding main:
        originalSecurityContext = ACL.impersonate(Jenkins.ANONYMOUS);
    }

    private void restoreAuth() {
        if (originalSecurityRealm != null) {
            rule.jenkins.setSecurityRealm(originalSecurityRealm);
            originalSecurityRealm = null;
        }

        if (originalAuthorizationStrategy != null) {
            rule.jenkins.setAuthorizationStrategy(originalAuthorizationStrategy);
            originalAuthorizationStrategy = null;
        }

        if (originalSecurityContext != null) {
            SecurityContextHolder.setContext(originalSecurityContext);
            originalSecurityContext = null;
        }
    }

    /**
     * @deprecated only used with {@link #authorizedTo}
     */
    @Deprecated
    public User user() {

        return User.get(username);
    }

    public static class Result {

        private final int result;
        private final ByteArrayOutputStream out;
        private final ByteArrayOutputStream err;

        private Result(
                final int result,
                final ByteArrayOutputStream out,
                final ByteArrayOutputStream err
        ) {

            this.result = result;
            this.out = out;
            this.err = err;
        }

        public int returnCode() {

            return result;
        }

        public String stdout() {

            return out.toString();
        }

        public byte[] stdoutBinary() {
            return out.toByteArray();
        }

        public String stderr() {

            return err.toString();
        }

        public byte[] stderrBinary() {
            return err.toByteArray();
        }

        @Override
        public String toString() {

            StringBuilder builder = new StringBuilder("CLI command exited with ").append(result);
            String stdout = stdout();
            if (!"".equals(stdout)) {
                builder.append("\nSTDOUT:\n").append(stdout);
            }
            String stderr = stderr();
            if (!"".equals(stderr)) {
                builder.append("\nSTDERR:\n").append(stderr);
            }

            return builder.toString();
        }
    }

    public abstract static class Matcher extends TypeSafeMatcher<Result> {

        private final String description;

        private Matcher(String description) {
            this.description = description;
        }

        @Override
        protected void describeMismatchSafely(Result result, Description description) {
            description.appendText(result.toString());
        }

        public void describeTo(Description description) {
            description.appendText(this.description);
        }

        public static Matcher hasNoStandardOutput() {
            return new Matcher("No standard output") {
                @Override protected boolean matchesSafely(Result result) {
                    return "".equals(result.stdout());
                }
            };
        }

        public static Matcher hasNoErrorOutput() {
            return new Matcher("No error output") {
                @Override protected boolean matchesSafely(Result result) {
                    return "".equals(result.stderr());
                }
            };
        }

        public static Matcher succeeded() {
            return new Matcher("Exited with 0 return code") {
                @Override protected boolean matchesSafely(Result result) {
                    return result.result == 0;
                }
            };
        }

        public static Matcher succeededSilently() {
            return new Matcher("Succeeded silently") {
                @Override protected boolean matchesSafely(Result result) {
                    return result.result == 0 && "".equals(result.stderr()) && "".equals(result.stdout());
                }
            };
        }

        public static Matcher failedWith(final long expectedCode) {
            return new Matcher("Exited with " + expectedCode + " return code") {
                @Override protected boolean matchesSafely(Result result) {
                    return result.result == expectedCode;
                }
            };
        }
    }
}
