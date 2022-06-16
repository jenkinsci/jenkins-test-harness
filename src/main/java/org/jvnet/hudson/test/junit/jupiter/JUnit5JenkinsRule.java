package org.jvnet.hudson.test.junit.jupiter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.runner.Description;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Provides JUnit 5 compatibility for {@link JenkinsRule}.
 */
class JUnit5JenkinsRule extends JenkinsRule {
    private final ParameterContext context;

    JUnit5JenkinsRule(@NonNull ParameterContext context, @NonNull ExtensionContext extensionContext) {
        this.context = context;
        this.testDescription = Description.createTestDescription(
                extensionContext.getTestClass().map(Class::getName).orElse(null),
                extensionContext.getTestMethod().map(Method::getName).orElse(null));
    }

    @Override
    public void recipe() throws Exception {
        JenkinsRecipe jenkinsRecipe = context.findAnnotation(JenkinsRecipe.class).orElse(null);
        if (jenkinsRecipe == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        final JenkinsRecipe.Runner<JenkinsRecipe> runner =
                (JenkinsRecipe.Runner<JenkinsRecipe>) jenkinsRecipe.value().getDeclaredConstructor().newInstance();
        recipes.add(runner);
        tearDowns.add(() -> runner.tearDown(this, jenkinsRecipe));
    }
}
