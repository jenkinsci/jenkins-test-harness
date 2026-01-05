package org.jvnet.hudson.test.junit.jupiter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.ACL;
import java.lang.annotation.Annotation;
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
                extensionContext.getTestMethod().map(Method::getAnnotations).orElse(new Annotation[0]));
    }

    @Override
    public void recipe() throws Exception {
        // so that test code has all the access to the system
        ACL.as2(ACL.SYSTEM2);

        if (extensionContext.getTestMethod().isPresent()) {
            // WithLocalData does not implement JenkinsRecipe
            Method testMethod = extensionContext.getTestMethod().get();
            WithLocalData localData = testMethod.getAnnotation(WithLocalData.class);
            if (localData == null && extensionContext.getTestClass().isPresent()) {
                Class<?> testClass = extensionContext.getTestClass().get();
                localData = testClass.getAnnotation(WithLocalData.class);
            }

            if (localData != null) {
                with(new HudsonHomeLoader.Local(testMethod, localData.value()));
            }
        }

        // other JenkinsRecipes handled here
        super.recipe();
    }
}
