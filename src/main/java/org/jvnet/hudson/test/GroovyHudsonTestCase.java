package org.jvnet.hudson.test;

import groovy.lang.Closure;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Builder;
import java.io.IOException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * {@link HudsonTestCase} with more convenience methods for Groovy.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated Use {@link GroovyJenkinsRule} instead.
 */
@Deprecated
public abstract class GroovyHudsonTestCase extends HudsonTestCase {
    /**
     * Executes the given closure on the server, in the context of an HTTP request.
     * This is useful for testing some methods that require {@link StaplerRequest} and {@link StaplerResponse}.
     * <p>
     * The closure will get the request and response as parameters.
     */
    public Object executeOnServer(final Closure<?> c) throws Exception {
        return executeOnServer(c::call);
    }

    /**
     * Wraps a closure as a {@link Builder}.
     */
    public Builder builder(final Closure<?> c) {
        return new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                Object r = c.call(build, launcher, listener);
                if (r instanceof Boolean)   return (Boolean)r;
                return true;
            }
        };
    }
}
