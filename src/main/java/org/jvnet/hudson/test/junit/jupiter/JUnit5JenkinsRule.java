package org.jvnet.hudson.test.junit.jupiter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.ACL;
import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.runner.Description;
import org.jvnet.hudson.test.HudsonHomeLoader;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Provides JUnit 5 compatibility for {@link JenkinsRule}.
 */
class JUnit5JenkinsRule extends JenkinsRule {
    private final ParameterContext parameterContext;
    private final ExtensionContext extensionContext;

    JUnit5JenkinsRule(@NonNull ParameterContext parameterContext, @NonNull ExtensionContext extensionContext) {
        this.parameterContext = parameterContext;
        this.extensionContext = extensionContext;
        this.testDescription = Description.createTestDescription(
                extensionContext.getTestClass().map(Class::getName).orElse(null),
                extensionContext.getTestMethod().map(Method::getName).orElse(null),
                extensionContext.getTestMethod().map(Method::getAnnotations).orElse(null));
    }

    @Override
    public void recipe() throws Exception {
        // so that test code has all the access to the system
        ACL.as2(ACL.SYSTEM2);

        // WithLocalData does not implement JenkinsRecipe
        WithLocalData withLocalData =
                extensionContext.getTestMethod().orElseThrow().getAnnotation(WithLocalData.class);
        if (withLocalData == null) {
            Class<?> testClass = extensionContext.getTestClass().orElseThrow();
            withLocalData = testClass.getAnnotation(WithLocalData.class);
        }
        if (withLocalData != null) {
            with(new HudsonHomeLoader.Local(extensionContext.getTestMethod().orElseThrow(), withLocalData.value()));
        }

        // other JenkinsRecipes handled here
        super.recipe();
    }
}
