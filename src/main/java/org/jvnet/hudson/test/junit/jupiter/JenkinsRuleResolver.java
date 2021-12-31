package org.jvnet.hudson.test.junit.jupiter;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * JUnit 5 extension providing {@link JenkinsRule} integration.
 *
 * @see org.jvnet.hudson.test.junit.jupiter.JenkinsRule
 */
public class JenkinsRuleResolver implements ParameterResolver, AfterEachCallback {

	private static final String key = "jenkins-instance";
	private static final ExtensionContext.Namespace namespace = ExtensionContext.Namespace.create(JenkinsRuleResolver.class);

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		final JenkinsRule rule = context.getStore(namespace).remove(key, JenkinsRule.class);

		if (rule != null) {
			rule.after();
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
		return parameterContext.getParameter().getType().equals(JenkinsRuleExtension.class);
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
		final JenkinsRule rule = extensionContext.getStore(namespace).getOrComputeIfAbsent(key, key
				-> new JenkinsRuleExtension(parameterContext, extensionContext), JenkinsRule.class);

		try {
			rule.before();
			return rule;
		} catch (Throwable t) {
			throw new ParameterResolutionException(t.getMessage(), t);
		}
	}
}
