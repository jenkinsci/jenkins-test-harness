package org.jvnet.hudson.test.junit.jupiter;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.runner.Description;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Provides JUnit 5 compatibility for {@link JenkinsRule}.
 */
public class JenkinsRuleExtension extends JenkinsRule {
    private final ParameterContext context;

    JenkinsRuleExtension(@NonNull ParameterContext context, @NonNull ExtensionContext extensionContext) {
        this.context = context;
        this.testDescription = Description.createTestDescription(extensionContext.getTestClass().map(Class::getName).orElse(null),
                extensionContext.getTestMethod().map(Method::getName).orElse(null));
    }

    @Override
    public void recipe() throws Exception {
        final Optional<JenkinsRecipe> jenkinsRecipe = context.findAnnotation(JenkinsRecipe.class);

        if (jenkinsRecipe.isPresent()) {
            @SuppressWarnings("unchecked") final JenkinsRecipe.Runner<JenkinsRecipe> runner = jenkinsRecipe.get()
                    .value().getDeclaredConstructor().newInstance();
            recipes.add(runner);
            tearDowns.add(() -> runner.tearDown(this, jenkinsRecipe.get()));
        }
    }
}
