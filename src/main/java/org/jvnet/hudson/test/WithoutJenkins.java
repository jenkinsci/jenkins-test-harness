package org.jvnet.hudson.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.jvnet.hudson.test.recipes.Recipe;
import org.jvnet.hudson.test.recipes.WithPlugin;

/**
 * An annotation for test methods that do not require the {@link JenkinsRule}/{@link HudsonTestCase} to create and tear down the jenkins
 * instance.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.METHOD)
@Recipe(WithoutJenkins.RunnerImpl.class)
public @interface WithoutJenkins {
    class RunnerImpl extends Recipe.Runner<WithPlugin> {
        // bogus. this recipe is handled differently by HudsonTestCase
    }
}
