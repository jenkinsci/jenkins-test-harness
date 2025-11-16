package org.jvnet.hudson.test.junit.jupiter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.ACL;
import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.runner.Description;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.jvnet.hudson.test.recipes.WithPluginManager;

/**
 * Provides JUnit 5 compatibility for {@link JenkinsRule}.
 */
class JUnit5JenkinsRule extends JenkinsRule {
    private final ParameterContext context;
    private final ExtensionContext extensionContext;

    JUnit5JenkinsRule(@NonNull ParameterContext context, @NonNull ExtensionContext extensionContext) {
        this.context = context;
        this.extensionContext = extensionContext;
        this.testDescription = Description.createTestDescription(
                extensionContext.getTestClass().map(Class::getName).orElse(null),
                extensionContext.getTestMethod().map(Method::getName).orElse(null));
    }

    @Override
    public void recipe() throws Exception {
        // so that test code has all the access to the system
        ACL.as2(ACL.SYSTEM2);

        JenkinsRecipe jenkinsRecipe =
                context.findAnnotation(JenkinsRecipe.class).orElse(null);
        if (jenkinsRecipe != null) {
            @SuppressWarnings("unchecked")
            final JenkinsRecipe.Runner<JenkinsRecipe> runner = (JenkinsRecipe.Runner<JenkinsRecipe>)
                    jenkinsRecipe.value().getDeclaredConstructor().newInstance();
            recipes.add(runner);
            tearDowns.add(() -> runner.tearDown(this, jenkinsRecipe));
        }

        Method testMethod = extensionContext.getTestMethod().orElse(null);
        if (testMethod != null) {
            LocalData localData = testMethod.getAnnotation(LocalData.class);
            if (localData != null) {
                final JenkinsRecipe.Runner<LocalData> runner = new LocalData.RuleRunnerImpl();
                recipes.add(runner);
                runner.setup(this, localData);
                tearDowns.add(() -> runner.tearDown(this, localData));
            }

            WithPlugin withPlugin = testMethod.getAnnotation(WithPlugin.class);
            if (withPlugin != null) {
                final JenkinsRecipe.Runner<WithPlugin> runner = new WithPlugin.RuleRunnerImpl();
                recipes.add(runner);
                runner.setup(this, withPlugin);
                tearDowns.add(() -> runner.tearDown(this, withPlugin));
            }

            WithPluginManager withPluginManager = testMethod.getAnnotation(WithPluginManager.class);
            if (withPluginManager != null) {
                final JenkinsRecipe.Runner<WithPluginManager> runner = new WithPluginManager.RuleRunnerImpl();
                recipes.add(runner);
                runner.setup(this, withPluginManager);
                tearDowns.add(() -> runner.tearDown(this, withPluginManager));
            }
        }
    }
}
