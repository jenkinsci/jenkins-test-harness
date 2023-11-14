package org.jvnet.hudson.test.junit.jupiter;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.runner.Description;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Provides JUnit 5 compatibility for {@link JenkinsRule}.
 */
class JUnit5JenkinsRule extends JenkinsRule {

    JUnit5JenkinsRule(@NonNull ExtensionContext extensionContext, Annotation... annotations) {
        this.testDescription = Description.createTestDescription(
            extensionContext.getTestClass().map(Class::getName).orElse(null),
            extensionContext.getTestMethod().map(Method::getName).orElse(null),
            annotations);
    }

    @Override
    public void recipe() throws Exception {
        final JenkinsRecipe recipe = this.testDescription.getAnnotation(JenkinsRecipe.class);

        if (recipe == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        final JenkinsRecipe.Runner<JenkinsRecipe> runner =
                (JenkinsRecipe.Runner<JenkinsRecipe>) recipe.value().getDeclaredConstructor().newInstance();
        recipes.add(runner);
        tearDowns.add(() -> runner.tearDown(this, recipe));
    }
}
