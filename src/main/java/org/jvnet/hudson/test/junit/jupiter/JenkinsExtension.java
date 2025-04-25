package org.jvnet.hudson.test.junit.jupiter;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.jvnet.hudson.test.HudsonHomeLoader.Local;
import org.jvnet.hudson.test.JenkinsRule;

import java.lang.reflect.Method;

/**
 * JUnit 5 extension providing {@link JenkinsRule} integration.
 *
 * @see WithJenkins
 */
class JenkinsExtension implements ParameterResolver, AfterEachCallback {

    private static final String KEY = "jenkins-instance";
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(JenkinsExtension.class);

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        final JenkinsRule rule = context.getStore(NAMESPACE).remove(KEY, JenkinsRule.class);
        if (rule == null) {
            return;
        }
        rule.after();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(JenkinsRule.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        final JenkinsRule rule =
                extensionContext
                        .getStore(NAMESPACE)
                        .getOrComputeIfAbsent(
                                KEY,
                                key -> new JUnit5JenkinsRule(parameterContext, extensionContext),
                                JenkinsRule.class);

        if(extensionContext.getTestMethod().isPresent()) {
            // check for a WithLocalData annotation to set up JENKINS_HOME
            Method testMethod = extensionContext.getTestMethod().get();
            WithLocalData localData = testMethod.getAnnotation(WithLocalData.class);
            if(localData == null && extensionContext.getTestClass().isPresent()) {
                Class<?> testClass = extensionContext.getTestClass().get();
                localData = testClass.getAnnotation(WithLocalData.class);
            }

            if(localData != null) {
                rule.with(new Local(testMethod, localData.value()));
            }
        }

        try {
            rule.before();
            return rule;
        } catch (Throwable t) {
            throw new ParameterResolutionException(t.getMessage(), t);
        }
    }
}
