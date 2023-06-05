package org.jvnet.hudson.test;

import groovy.lang.Closure;
import hudson.PluginManager;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.rules.MethodRule;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * Provides a pattern for executing a sequence of steps.
 * In between steps, Jenkins gets restarted.
 *
 * <p>
 * To use this, add this rule instead of {@link JenkinsRule} to the test,
 * then from your test method, call {@link #then} repeatedly.
 * You may test scenarios related to abrupt shutdowns or failures to start using {@link #thenWithHardShutdown} and
 * {@link #thenDoesNotStart}.
 * <p>
 * The rule will evaluate your test method to collect all steps, then execute them in turn and restart Jenkins in
 * between each step. Consider using {@link JenkinsSessionRule} if you want each step to be executed immediately when
 * {@link #then} is called.
 * <p>
 * If your test requires disabling of a plugin then the default {@link PluginManager} ({@link TestPluginManager}) used for tests
 * will need to be changed to {@link UnitTestSupportingPluginManager}.
 * This can be accomplished by annotating the test with {@code @WithPluginManager(UnitTestSupportingPluginManager.class)}.
 * 
 * @author Kohsuke Kawaguchi
 * @see JenkinsRule
 * @since 1.567
 */
public class RestartableJenkinsRule implements MethodRule {
    public JenkinsRule j;
    private Description description;

    /**
     * List of {@link Statement}. For each one, the boolean value says if Jenkins is expected to start or not.
     */
    private final Map<Statement, Boolean> steps = new LinkedHashMap<>();

    private final TemporaryDirectoryAllocator tmp = new TemporaryDirectoryAllocator();

    /**
     * Object that defines a test.
     */
    private Object target;

    /**
     * JENKINS_HOME
     */
    public File home;

    /**
     * TCP/IP port that the server is listening on.
     */
    private final int port;

    private static final Logger LOGGER = Logger.getLogger(HudsonTestCase.class.getName());

    public static class Builder {
        private int port;

        public Builder() {
            this.port = 0;
        }

        public Builder withReusedPort() {
            this.port = getRandomPort();
            return this;
        }

        public RestartableJenkinsRule build() {
            return new RestartableJenkinsRule(this.port);
        }
    }

    public RestartableJenkinsRule() {
        this.port = 0;
    }

    private RestartableJenkinsRule(int port) {
        this.port = port;
    }

    @Override
    public Statement apply(final Statement base, FrameworkMethod method, Object target) {
        this.description = Description.createTestDescription(
                method.getMethod().getDeclaringClass(), method.getName(), method.getAnnotations());

        this.target = target;

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // JENKINS_HOME needs to survive restarts, so we'll allocate our own
                try {
                    home = tmp.allocate();

                    // test method will accumulate steps
                    base.evaluate();
                    // and we'll run them
                    run();
                } finally {
                    try {
                        tmp.dispose();
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            }
        };
    }

    /**
     * @deprecated Use {@link #then} instead.
     */
    @Deprecated
    public void step(final Closure<?> c) {
        addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                c.call(j);
            }
        });
    }

    /** Approach adapted from https://stackoverflow.com/questions/6214703/copy-entire-directory-contents-to-another-directory */
    static class CopyFileVisitor extends SimpleFileVisitor<Path> {
        private final Path targetPath;
        private Path sourcePath = null;
        public CopyFileVisitor(Path targetPath) {
            this.targetPath = targetPath;
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir,
                                                 final BasicFileAttributes attrs) throws IOException {
            if (sourcePath == null) {
                sourcePath = dir;
            } else {
                Files.createDirectories(targetPath.resolve(sourcePath
                        .relativize(dir)));
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file,
                                         final BasicFileAttributes attrs) throws IOException {
            try {
                if (!Files.isSymbolicLink(file)) {
                    // Needed because Jenkins includes invalid lastSuccessful symlinks and otherwise we get a NoSuchFileException
                    Files.copy(file,
                            targetPath.resolve(sourcePath.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                } else if (Files.isSymbolicLink(file) && Files.exists(Files.readSymbolicLink(file))) {
                    Files.copy(file,
                            targetPath.resolve(sourcePath.relativize(file)), LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
                }
            } catch (NoSuchFileException nsfe) {
                // File removed in between scan beginning and when we try to copy it, ignore it
                LOGGER.log(Level.FINE, "File disappeared while trying to copy to new home, continuing anyway: "+ file);
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            if (exc instanceof FileNotFoundException) {
                LOGGER.log(Level.FINE, "File not found while trying to copy to new home, continuing anyway: " + file.toString());
                return FileVisitResult.CONTINUE;
            } else if (exc instanceof NoSuchFileException) {
                LOGGER.log(Level.FINE, "File disappeared while trying to copy to new home, continuing anyway: " + file.toString());
                return FileVisitResult.CONTINUE;
            } else {
                LOGGER.log(Level.WARNING, "Error copying file", exc);
                return FileVisitResult.TERMINATE;
            }
        }
    }

    /**
     * Simulate an abrupt failure of Jenkins to see if it appropriately handles inconsistent states when
     *  shutdown cleanup is not performed or data is not written fully to disk.
     *
     * Works by copying the JENKINS_HOME to a new directory and then setting the {@link RestartableJenkinsRule} to use
     * that for the next restart. Thus we only have the data actually persisted to disk at that time to work with.
     *
     * Should be run as the last part of a {@link org.jvnet.hudson.test.RestartableJenkinsRule.Step}.
     */
     void simulateAbruptShutdown() throws IOException {
         LOGGER.log(Level.INFO, "Beginning snapshot of JENKINS_HOME so we can simulate abrupt shutdown.  Disk writes MAY be lost if they happen after this.");
         File homeDir = this.home;
         File newHome = tmp.allocate();

         // Copy efficiently
         Files.walkFileTree(homeDir.toPath(), Set.of(), 99, new CopyFileVisitor(newHome.toPath()));
         LOGGER.log(Level.INFO, "Finished snapshot of JENKINS_HOME, any disk writes by Jenkins after this are lost as we will simulate suddenly killing the Jenkins process and switch to the snapshot.");
         home = newHome;
    }

    /**
     * One step to run, intended to be a SAM for lambdas with {@link #then}.
     * {@link Closure} does not work because it is an abstract class, not an interface.
     * {@link Callable} of {@link Void} does not work because you have to return null.
     * {@link Runnable} does not work because it throws no checked exceptions.
     * {@code Consumer} is the same, and is not present in Java 7.
     * Other candidates had similar issues.
     */
    @FunctionalInterface
    public interface Step {
        void run(JenkinsRule r) throws Throwable;
    }
    /**
     * Run one Jenkins session and shut down.
     * @since 2.24
     */
    public void then(final Step s) {
        addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                s.run(j);
            }
        });
    }

    /**
     * Run one Jenkins session and then simulate the Jenkins process ending without a clean shutdown.
     * This can be used to test that data is appropriately persisted without relying on shutdown processes.
     * <p><strong>Implementation note:</strong> we're actually just copying the JENKINS_HOME, which takes some time -
     *  so the shutdown isn't truly instant (additional data may be written while this happens).
     */
    public void thenWithHardShutdown(final Step s) {
        addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                s.run(j);
                simulateAbruptShutdown();
            }
        });
    }

    public void thenDoesNotStart() {
        addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                throw new IllegalStateException("should have failed before reaching here.");
            }
        }, false);
    }

    /**
     * @deprecated Use {@link #then} instead.
     */
    @Deprecated
    public void addStep(final Statement step) {
        addStep(step, true);
    }

    /**
     * @deprecated Use {@link #then} or {@link #thenDoesNotStart} instead.
     */
    @Deprecated
    public void addStep(final Statement step, boolean expectedToStartCorrectly) {
        steps.put(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                j.jenkins.getInjector().injectMembers(step);
                j.jenkins.getInjector().injectMembers(target);
                step.evaluate();
            }
        }, expectedToStartCorrectly);
    }

    /**
     * @deprecated Use {@link #thenWithHardShutdown} instead.
     */
    @Deprecated
    public void addStepWithDirtyShutdown(final Statement step) {
        steps.put(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                j.jenkins.getInjector().injectMembers(step);
                j.jenkins.getInjector().injectMembers(target);
                step.evaluate();
                simulateAbruptShutdown();
            }
        }, true);
    }

    private void run() throws Throwable {
        HudsonHomeLoader loader = () -> home;

        // run each step inside its own JenkinsRule
        for (Map.Entry<Statement, Boolean> entry : steps.entrySet()) {
            Statement step = entry.getKey();
            j = createJenkinsRule(description).with(loader);
            try {
                j.apply(step, description).evaluate();
                if (!entry.getValue()) {
                    Assert.fail("The current JenkinsRule should have failed to start Jenkins.");
                }
            } catch (Exception e) {
                if(entry.getValue()) {
                    throw e;
                }
                // Failure ignored as requested
            }
        }
    }

    protected JenkinsRule createJenkinsRule(Description description) {
        JenkinsRule result = new JenkinsRule();
        if (port != 0) {
            result.localPort = port;
        }
        return result;
    }

    private static synchronized int getRandomPort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to find free port", e);
        }
    }
}
