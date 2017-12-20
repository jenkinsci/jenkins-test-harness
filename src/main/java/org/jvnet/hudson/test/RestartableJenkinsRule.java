package org.jvnet.hudson.test;

import groovy.lang.Closure;
import hudson.FilePath;
import org.junit.rules.MethodRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Provides a pattern for executing a sequence of steps.
 * In between steps, Jenkins gets restarted.
 *
 * <p>
 * To use this, add this rule instead of {@link JenkinsRule} to the test,
 * then from your test method, call {@link #step(Closure)} or {@link #addStep(Statement)} repeatedly.
 *
 * <p>
 * The rule will evaluate your test method to collect all steps, then execute them in turn and restart
 * Jenkins in between.
 *
 * @author Kohsuke Kawaguchi
 * @see JenkinsRule
 * @since 1.567
 */
public class RestartableJenkinsRule implements MethodRule {
    public JenkinsRule j;
    private Description description;
    private final List<Statement> steps = new ArrayList<Statement>();

    private TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Object that defines a test.
     */
    private Object target;

    /**
     * JENKINS_HOME
     */
    public File home;


    @Override
    public Statement apply(final Statement base, FrameworkMethod method, Object target) {
        this.description = Description.createTestDescription(
                method.getMethod().getDeclaringClass(), method.getName(), method.getAnnotations());

        this.target = target;

        return tmp.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // JENKINS_HOME needs to survive restarts, so we'll allocate our own
                home = tmp.newFolder();

                // test method will accumulate steps
                base.evaluate();
                // and we'll run them
                run();
            }
        }, description);
    }

    public void step(final Closure c) {
        addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                c.call(j);
            }
        });
    }

    /**
     * Simulate an abrupt failure of Jenkins to see if it appropriately handles inconsistent states when
     *  shutdown cleanup is not performed or data is not written fully to disk.
     *
     * Works by copying the JENKINS_HOME to a new directory and then setting the {@link RestartableJenkinsRule} to use
     * that for the next restart. Thus we only have the data actually persisted to disk at that time to work with.
     *
     * Should be run as the last part of a {@link org.jvnet.hudson.test.RestartableJenkinsRule.Step}.
     *
     * @throws IOException
     */
     void simulateAbruptShutdown() throws IOException {
        File homeDir = this.home;
        TemporaryFolder temp = new TemporaryFolder();
        temp.create();
        File newHome = temp.newFolder();

        // Copy efficiently
         try {
             try {
                new FilePath(homeDir).copyRecursiveTo(new FilePath(newHome));
             } catch (NoSuchFileException nsfe) {
                // Retry in case of tempfile deletion while copying.
                newHome.delete();
                newHome = temp.newFolder();
                new FilePath(homeDir).copyRecursiveTo(new FilePath(newHome));
             }
         } catch (InterruptedException ie) {
             throw new IOException(ie);
         }
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
    // TODO Java 8: @FunctionalInterface
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

    public void addStep(final Statement step) {
        steps.add(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                j.jenkins.getInjector().injectMembers(step);
                j.jenkins.getInjector().injectMembers(target);
                step.evaluate();
            }
        });
    }

    /** Similar to {@link #addStep(Statement)} but we simulate a dirty shutdown after the step, rather than a clean one.
     *  See {@link #thenWithHardShutdown(Step)} for how this is done.
     */
    public void addStepWithDirtyShutdown(final Statement step) {
        steps.add(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                j.jenkins.getInjector().injectMembers(step);
                j.jenkins.getInjector().injectMembers(target);
                step.evaluate();
                simulateAbruptShutdown();
            }
        });
    }

    private void run() throws Throwable {
        HudsonHomeLoader loader = new HudsonHomeLoader() {
            @Override
            public File allocate() throws Exception {
                return home;
            }
        };

        // run each step inside its own JenkinsRule
        for (Statement step : steps) {
            j = createJenkinsRule(description).with(loader);
            j.apply(step,description).evaluate();
        }
    }

    protected JenkinsRule createJenkinsRule(Description description) {
        return new JenkinsRule();
    }

}
