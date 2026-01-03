package org.jvnet.hudson.test.junit.jupiter;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * JUnit 5 extension providing {@link JenkinsRule} integration.
 *
 * @see WithJenkins
 */
public class JenkinsExtension implements ParameterResolver, AfterEachCallback {

    private static final String KEY = "jenkins-instance";
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(JenkinsExtension.class);

    @Override
    public void afterEach(@NonNull ExtensionContext context) throws Exception {
        final JenkinsRule rule = context.getStore(NAMESPACE).remove(KEY, JenkinsRule.class);
        if (rule != null) {
            rule.after();
        }
    }

    @Override
    public boolean supportsParameter(
            @NonNull ParameterContext parameterContext, @NonNull ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(JenkinsRule.class);
    }

    @Override
    public Object resolveParameter(
            @NonNull ParameterContext parameterContext, @NonNull ExtensionContext extensionContext)
            throws ParameterResolutionException {
        final JenkinsRule rule = extensionContext
                .getStore(NAMESPACE)
                .getOrComputeIfAbsent(
                        KEY, key -> new JUnit5JenkinsRule(parameterContext, extensionContext), JenkinsRule.class);
        try {
            rule.before();
            return rule;
        } catch (Throwable t) {
            throw new ParameterResolutionException(t.getMessage(), t);
        }
    }
}
